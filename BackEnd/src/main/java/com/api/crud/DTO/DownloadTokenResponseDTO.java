package com.api.crud.DTO;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

public class DownloadTokenResponseDTO {

    @Getter
    @Setter
    private String token;

    @Getter
    @Setter
    private LocalDateTime expiresAt;

    @Getter
    @Setter
    private String downloadPath;
}
