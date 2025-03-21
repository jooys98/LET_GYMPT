package com.example.gympt.domain.trainer.dto;

import jakarta.persistence.OneToMany;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@ToString
public class TrainerSaveRequestDTO {
    //트레이너 신청 양식
    private String email;
    private String name;
    private String introduction;
    private Long age;
    private Long gymId;
    private String gymName;
    private MultipartFile profileImage;

    private List<MultipartFile> files;

//엑셀용
    private String profileImageUrl;
    private List<String> uploadFileNames;

}
