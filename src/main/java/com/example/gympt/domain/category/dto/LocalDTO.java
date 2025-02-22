package com.example.gympt.domain.category.dto;

import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString

public class LocalDTO {
    private Long id;
    private String localName;

}
