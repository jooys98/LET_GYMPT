package com.example.gympt.domain.reverseAuction.service;

import com.example.gympt.domain.category.entity.Local;
import com.example.gympt.domain.category.repository.LocalRepository;
import com.example.gympt.domain.member.entity.Member;
import com.example.gympt.domain.member.enums.MemberRole;
import com.example.gympt.domain.member.repository.MemberRepository;
import com.example.gympt.domain.reverseAuction.dto.*;
import com.example.gympt.domain.reverseAuction.entity.AuctionRequest;
import com.example.gympt.domain.reverseAuction.entity.AuctionTrainerBid;
import com.example.gympt.domain.reverseAuction.entity.MatchedAuction;
import com.example.gympt.domain.reverseAuction.enums.AuctionStatus;
import com.example.gympt.domain.reverseAuction.repository.AuctionRequestRepository;
import com.example.gympt.domain.reverseAuction.repository.AuctionTrainerBidRepository;
import com.example.gympt.domain.reverseAuction.repository.MatchedAuctionRepository;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.message.SimpleMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ReverseAuctionServiceImpl implements ReverseAuctionService {

    private final MemberRepository memberRepository;
    private final AuctionRequestRepository auctionRequestRepository;
    private final LocalRepository localRepository;
    private final AuctionTrainerBidRepository auctionTrainerBidRepository;
    private final MatchedAuctionRepository matchedAuctionRepository;
    private final SimpMessagingTemplate messagingTemplate; //웹소켓 전송용


    //유저의 역경매 신청 로직
    @Override
    public void applyAuction(AuctionRequestDTO auctionRequestDTO) {
        Member member = memberRepository.getWithRoles(auctionRequestDTO.getEmail()).orElseThrow(() -> new RuntimeException("존재하지 않는 회원입니다"));

        if (!member.getMemberRoleList().contains(MemberRole.USER)) {
            throw new IllegalArgumentException("역경매 신청은 일반 회원만 가능합니다 ");
        }

        Boolean exits = auctionRequestRepository.existsByMember_Email(member.getEmail());

        if (exits) {
            throw new RuntimeException("역경매 줄복 신청은 불가능 합니다");
            //TODO: custom exception handler로 바꾸기
        } else {

            Local local = localRepository.findByLocalName(auctionRequestDTO.getLocalName()).orElseThrow();
            //사용자는 자신의 사는 지역이 아니더라도 원하는 지역에서 수업 신청이 가능하므로 동네 조회 비교 로직은 생략

            AuctionRequest auctionRequest = AuctionRequest.builder()
                    .member(member)
                    .height(auctionRequestDTO.getHeight())
                    .gender(auctionRequestDTO.getGender())
                    .weight(auctionRequestDTO.getWeight())
                    .title(auctionRequestDTO.getTitle())
                    .age(auctionRequestDTO.getAge())
                    .request(auctionRequestDTO.getRequest())
                    .medicalConditions(auctionRequestDTO.getMedicalConditions())
                    .local(local)
                    .build();
            auctionRequest.setStatus(Collections.singletonList(AuctionStatus.OPEN));

            auctionRequestRepository.save(auctionRequest);
        }
    }

    @Transactional
    @Override
    public FinalSelectAuctionDTO selectTrainer(String email, String trainerEmail) {
        Member member = memberRepository.getWithRoles(email).orElseThrow(() -> new RuntimeException("존재하지 않는 회원입니다"));

        AuctionRequest auctionRequest = auctionRequestRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("신청 내역이 없습니다"));

        if (!auctionRequest.getStatus().contains(AuctionStatus.IN_PROGRESS)) {
            throw new RuntimeException("입찰한 트레이너가 없습니다 "); // 트래이너 입찰 시 IN_PROGRESS 상태 변경
        } else if (!auctionRequest.getMember().equals(member)) {
            throw new RuntimeException("경매 신청자 본인확인이 안되어 권한이 없습니다 ");
        }


        auctionRequest.setStatus(Collections.singletonList(AuctionStatus.COMPLETED)); // 최종 선택시 상태 변경
        auctionRequestRepository.save(auctionRequest);

        AuctionTrainerBid auctionTrainerBid = auctionTrainerBidRepository.findByEmail(trainerEmail)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 트레이너 입니다"));

        MatchedAuction matchedAuction = MatchedAuction.builder()
                .auctionTrainerBid(auctionTrainerBid)
                .auctionRequest(auctionRequest)
                .finalPrice(auctionTrainerBid.getPrice()) // 트레이너가 제안한 최종 금액
                .build();
        matchedAuctionRepository.save(matchedAuction);

        FinalSelectAuctionDTO finalSelectAuctionDTO = FinalSelectAuctionDTO.builder()
                .auctionId(auctionRequest.getId())
                .email(matchedAuction.getAuctionRequest().getMember().getEmail())
                .finalPrice(matchedAuction.getFinalPrice())
                .trainerName(matchedAuction.getAuctionTrainerBid().getTrainer().getTrainerName())
                .closedAt(matchedAuction.getClosedAt())
                .localName(String.valueOf(matchedAuction.getAuctionTrainerBid().getTrainer().getLocal()))
                .gymAddress(matchedAuction.getAuctionTrainerBid().getTrainer().getGym().getAddress())
                .build();

        AuctionTrainerNotificationDTO notificationDTO = AuctionTrainerNotificationDTO.builder()
                .type("AUCTION_SELECTED")
                .memberEmail(email)
                .memberPhoneNumber(matchedAuction.getAuctionRequest().getMember().getPhone())
                .memberName(matchedAuction.getAuctionRequest().getMember().getName())
                .auctionId(auctionRequest.getId())
                .finalPrice(matchedAuction.getFinalPrice())
                .message("축하드립니다! 회원님이 귀하를 PT 트레이너로 선택했습니다")
                .build();

        // 웹소켓으로 트레이너에게 알림 전송
        messagingTemplate.convertAndSendToUser(
                trainerEmail,  // 트레이너의 이메일 (구독 식별자)
                "/queue/notifications",  // 클라이언트에서 받는 구독 주소
                notificationDTO
        );

        return finalSelectAuctionDTO;
    }
//유저에게만 보여지는 정보
    @Transactional
    @Override
    public List<AuctionResponseDTO> getAuctionList() {
        List<AuctionResponseDTO> auctionResponseDTOS = auctionRequestRepository.findAll().stream()
                .map(this::AuctionEntityToDTO).collect(Collectors.toList());
        return auctionResponseDTOS;
    }
//트레이너에게만 보여지는 정보
    @Override
    public List<AuctionResponseToTrainerDTO> getAuctionListToTrainers() {
        List<AuctionResponseToTrainerDTO> auctionResponseDTOS = auctionRequestRepository.findAll().stream()
                .map(this::AuctionEntityForTrainersToDTO).collect(Collectors.toList());
        return auctionResponseDTOS;
    }

}
