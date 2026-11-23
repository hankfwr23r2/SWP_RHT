package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.policy.TrackingPolicy.CustodyHolder;
import com.example.racehorse_transport.entity.enums.HandoverType;
import org.junit.jupiter.api.Test;

class TrackingPolicyTest {

    @Test
    void holderFollowsTheReceivingSideOfTheLastHandover() {
        assertThat(TrackingPolicy.holderAfter(null, false)).isEqualTo(CustodyHolder.SHIPPER);
        assertThat(TrackingPolicy.holderAfter(HandoverType.PICKUP, false)).isEqualTo(CustodyHolder.COMPANY);
        assertThat(TrackingPolicy.holderAfter(HandoverType.HUB_IN, false)).isEqualTo(CustodyHolder.COMPANY);
        assertThat(TrackingPolicy.holderAfter(HandoverType.HUB_OUT, false)).isEqualTo(CustodyHolder.COMPANY);
        assertThat(TrackingPolicy.holderAfter(HandoverType.STAY_IN, true)).isEqualTo(CustodyHolder.PARTY);
        assertThat(TrackingPolicy.holderAfter(HandoverType.STAY_OUT, false)).isEqualTo(CustodyHolder.COMPANY);
        assertThat(TrackingPolicy.holderAfter(HandoverType.AGENT, true)).isEqualTo(CustodyHolder.PARTY);
        assertThat(TrackingPolicy.holderAfter(HandoverType.AGENT, false)).isEqualTo(CustodyHolder.COMPANY);
        assertThat(TrackingPolicy.holderAfter(HandoverType.DELIVERY, false)).isEqualTo(CustodyHolder.CONSIGNEE);
        assertThat(TrackingPolicy.holderAfter(HandoverType.RETURN, false)).isEqualTo(CustodyHolder.SHIPPER);
    }

    @Test
    void phoneSuffixIgnoresFormattingAndNeedsExactlyFourDigits() {
        assertThat(TrackingPolicy.phoneSuffixMatches("0909 000 001", "0001")).isTrue();
        assertThat(TrackingPolicy.phoneSuffixMatches("+855 12 000 001", "0001")).isTrue();
        assertThat(TrackingPolicy.phoneSuffixMatches("0909000001", "9001")).isFalse();
        assertThat(TrackingPolicy.phoneSuffixMatches("0909000001", "001")).isFalse();
        assertThat(TrackingPolicy.phoneSuffixMatches("123", "0123")).isFalse();
        assertThat(TrackingPolicy.phoneSuffixMatches(null, "0001")).isFalse();
    }
}
