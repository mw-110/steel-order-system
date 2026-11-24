package com.example.steelorder.repository;

import com.example.steelorder.entity.Coil;
import com.example.steelorder.entity.SteelSheet;
import com.example.steelorder.entity.enums.CoilStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CoilRepository extends JpaRepository<Coil, Long> {
    List<Coil> findByAvailableWeightGreaterThan(int availableWeight);

    List<Coil> findBySteelSheetAndStatusNotOrderByArrivalDateAsc(SteelSheet steelSheet, CoilStatus status);

    boolean existsByCoilNumber(String coilNumber);
}

