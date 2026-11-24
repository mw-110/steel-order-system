package com.example.steelorder.repository;

import com.example.steelorder.entity.Coil;
import com.example.steelorder.entity.SteelSheet;
import com.example.steelorder.entity.enums.CoilStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CoilOptimisticLockingIntegrationTest {

    @Autowired
    private CoilRepository coilRepository;

    @Autowired
    private SteelSheetRepository steelSheetRepository;

    @Test
    @DisplayName("同一コイルの競合更新時に楽観ロック例外が発生し、競合側トランザクションはロールバックされる")
    void optimisticLocking_throwsAndRollsBack_conflictingUpdate() {
        SteelSheet sheet = steelSheetRepository.findAll().getFirst();

        Coil seed = new Coil();
        seed.setCoilNumber("CL-OPT-LOCK-001");
        seed.setSteelSheet(sheet);
        seed.setInitialWeight(1000);
        seed.setPhysicalWeight(1000);
        seed.setAvailableWeight(1000);
        seed.setStatus(CoilStatus.AVAILABLE);
        seed.setArrivalDate(LocalDate.now());
        Coil persisted = coilRepository.saveAndFlush(seed);

        Coil tx1Snapshot = coilRepository.findById(persisted.getCoilId()).orElseThrow();
        Coil tx2Snapshot = coilRepository.findById(persisted.getCoilId()).orElseThrow();

        tx1Snapshot.setAvailableWeight(900);
        coilRepository.saveAndFlush(tx1Snapshot);

        tx2Snapshot.setAvailableWeight(800);
        assertThatThrownBy(() -> coilRepository.saveAndFlush(tx2Snapshot))
                .isInstanceOf(OptimisticLockingFailureException.class);

        Coil actual = coilRepository.findById(persisted.getCoilId()).orElseThrow();
        assertThat(actual.getAvailableWeight()).isEqualTo(900);
    }
}
