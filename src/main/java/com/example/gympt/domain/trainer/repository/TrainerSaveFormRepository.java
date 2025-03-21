package com.example.gympt.domain.trainer.repository;

import com.example.gympt.domain.trainer.entity.TrainerSaveForm;
import com.example.gympt.domain.trainer.entity.Trainers;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TrainerSaveFormRepository extends JpaRepository<TrainerSaveForm, Long> {

    @Query("select t from TrainerSaveForm t join t.member m where m.email = :email")
    Optional<TrainerSaveForm> findByEmail(@Param("email") String email);



}
