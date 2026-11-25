package com.example.racehorse_transport.dto.complaint;

import com.example.racehorse_transport.entity.Incident;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ComplaintReplyRequestDto(@NotBlank @Size(max = 4000) String reply) {

    public void applyTo(Incident complaint) {
        complaint.setComplaintReply(reply.strip());
    }
}
