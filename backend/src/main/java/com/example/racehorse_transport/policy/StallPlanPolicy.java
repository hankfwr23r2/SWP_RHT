package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.enums.HorseGender;
import com.example.racehorse_transport.exception.DispatchRuleException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Xếp vị trí khoang trên một lượt xe (quy tắc #39, NGHIEP_VU GĐ5), hàm thuần.
// Vị trí đánh số từ đầu xe (1) ra cửa sau. Xe là các đoạn liền nhau (vách di động: đợt này mỗi xe một đoạn, ví dụ [9]);
// con cần 2 chỗ (khoang rộng) chiếm vị trí p và p + 1 trong cùng một đoạn. Hai con trên xe cùng lúc không trùng vị trí.
public final class StallPlanPolicy {

    // Giới hạn số bước tìm kiếm: xe tối đa vài chục chỗ, vượt giới hạn coi như không xếp được
    private static final int SEARCH_LIMIT = 200_000;

    private StallPlanPolicy() {}

    // board, alight: số thứ tự điểm dừng lên và xuống. locked: con đã lên xe, giữ nguyên vị trí
    public record StallHorse(Long legId, String name, Long bookingId, HorseGender sex, int board, int alight, int seats,
                             Integer position, boolean locked) {

        boolean overlaps(StallHorse other) {
            return board < other.alight && other.board < alight;
        }
    }

    // Xếp lại cả xe: con đã khóa giữ chỗ, các con còn lại xếp từ đầu xe. Con lên sớm và xuống muộn đứng gần đầu xe,
    // con cùng đơn đứng cạnh nhau, ngựa đực về phía đầu xe. Không xếp được thì từ chối và nêu con vướng
    public static Map<Long, Integer> suggest(List<StallHorse> horses, List<Integer> segments) {
        Map<Long, Integer> placed = new HashMap<>();
        List<StallHorse> fixed = horses.stream().filter(StallHorse::locked).toList();
        for (StallHorse horse : fixed) {
            if (horse.position() == null || !fits(horse, horse.position(), fixed, placed, segments)) {
                throw new DispatchRuleException("stallPosition", "Vị trí của " + horse.name() + " đã lên xe không hợp lệ.", true);
            }
            placed.put(horse.legId(), horse.position());
        }
        List<StallHorse> free = horses.stream().filter(h -> !h.locked()).sorted(ORDER).toList();
        Search search = new Search(horses, segments);
        if (!search.place(free, 0, placed)) {
            StallHorse stuck = free.get(Math.min(search.deepest, free.size() - 1));
            throw new DispatchRuleException("stallPosition", "Không xếp được khoang cho " + stuck.name() + ": cần " + stuck.seats()
                    + " vị trí liền nhau còn trống suốt đoạn con đó ở trên xe.", true);
        }
        return placed;
    }

    // Đặt thêm một con mà không dời các con đã có vị trí: vị trí trống gần đầu xe nhất
    public static Optional<Integer> placeOne(StallHorse horse, List<StallHorse> others, List<Integer> segments) {
        Map<Long, Integer> placed = new HashMap<>();
        others.stream().filter(o -> o.position() != null).forEach(o -> placed.put(o.legId(), o.position()));
        int total = total(segments);
        for (int position = 1; position <= total; position++) {
            if (fits(horse, position, others, placed, segments)) return Optional.of(position);
        }
        return Optional.empty();
    }

    // Quy tắc #39: mọi con có vị trí, nằm gọn trong một đoạn của xe, không trùng con khác trên cùng đoạn đường
    public static void requireValid(List<StallHorse> horses, Map<Long, Integer> positions, List<Integer> segments) {
        Map<Long, Integer> placed = new HashMap<>();
        for (StallHorse horse : horses) {
            Integer position = positions.get(horse.legId());
            if (position == null) {
                throw new DispatchRuleException("stallPosition", horse.name() + " chưa có vị trí khoang.");
            }
            if (!fits(horse, position, horses, placed, segments)) {
                throw new DispatchRuleException("stallPosition", "Vị trí " + position + " của " + horse.name()
                        + (horse.seats() > 1 ? " (khoang rộng, cần hai vị trí liền nhau)" : "") + " trùng con khác hoặc vượt số chỗ của xe.");
            }
            placed.put(horse.legId(), position);
        }
    }

    // Ưu tiên không đạt, chỉ cảnh báo để điều phối quyết định
    public static List<String> warnings(List<StallHorse> horses, Map<Long, Integer> positions) {
        List<String> result = new ArrayList<>();
        for (StallHorse a : horses) {
            for (StallHorse b : horses) {
                Integer pa = positions.get(a.legId());
                Integer pb = positions.get(b.legId());
                if (a == b || pa == null || pb == null || !a.overlaps(b)) continue;
                if (a.sex() == HorseGender.STALLION && b.sex() == HorseGender.MARE && adjacent(a, pa, b, pb)) {
                    result.add("Ngựa đực " + a.name() + " đứng cạnh ngựa cái " + b.name() + ".");
                }
                // Xe chất và dỡ ngựa ở cửa sau: con đứng phía cửa (số lớn hơn) chắn con phía trong
                if (pa < pb && a.alight() < b.alight()) {
                    result.add(a.name() + " xuống trước nhưng bị " + b.name() + " chắn phía cửa.");
                }
                if (pa < pb && a.board() > b.board()) {
                    result.add(a.name() + " lên sau nhưng bị " + b.name() + " chắn phía cửa.");
                }
            }
        }
        return result;
    }

    public static int total(List<Integer> segments) {
        return segments.stream().mapToInt(Integer::intValue).sum();
    }

    private static final Comparator<StallHorse> ORDER = Comparator.comparingInt(StallHorse::board)
            .thenComparing(Comparator.comparingInt(StallHorse::alight).reversed())
            .thenComparing(StallHorse::bookingId, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(h -> h.sex() == HorseGender.STALLION ? 0 : 1)
            .thenComparing(StallHorse::legId);

    private static boolean adjacent(StallHorse a, int pa, StallHorse b, int pb) {
        return pa + a.seats() == pb || pb + b.seats() == pa;
    }

    // Con nằm gọn trong một đoạn và không trùng con nào đã đặt mà ở trên xe cùng lúc
    private static boolean fits(StallHorse horse, int position, List<StallHorse> others, Map<Long, Integer> placed, List<Integer> segments) {
        int last = position + horse.seats() - 1;
        int start = 1;
        boolean inSegment = false;
        for (int length : segments) {
            int end = start + length - 1;
            if (position >= start && last <= end) inSegment = true;
            start = end + 1;
        }
        if (position < 1 || !inSegment) return false;
        for (StallHorse other : others) {
            Integer at = placed.get(other.legId());
            if (other == horse || at == null || other.legId().equals(horse.legId()) || !horse.overlaps(other)) continue;
            if (position <= at + other.seats() - 1 && at <= last) return false;
        }
        return true;
    }

    private static final class Search {
        private final List<StallHorse> all;
        private final List<Integer> segments;
        private int steps;
        private int deepest;

        Search(List<StallHorse> all, List<Integer> segments) {
            this.all = all;
            this.segments = segments;
        }

        boolean place(List<StallHorse> free, int index, Map<Long, Integer> placed) {
            deepest = Math.max(deepest, index);
            if (index == free.size()) return true;
            StallHorse horse = free.get(index);
            int total = total(segments);
            for (int position = 1; position <= total; position++) {
                if (++steps > SEARCH_LIMIT) return false;
                if (!fits(horse, position, all, placed, segments)) continue;
                placed.put(horse.legId(), position);
                if (place(free, index + 1, placed)) return true;
                placed.remove(horse.legId());
            }
            return false;
        }
    }
}
