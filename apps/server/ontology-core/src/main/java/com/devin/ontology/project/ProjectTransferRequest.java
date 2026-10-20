package com.devin.ontology.project;

import jakarta.validation.constraints.NotBlank;

/** 所有权转移只接收目标内部用户标识。 */
public record ProjectTransferRequest(@NotBlank String targetUserId) {
}
