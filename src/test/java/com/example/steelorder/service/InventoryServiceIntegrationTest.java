package com.example.steelorder.service;

import com.example.steelorder.dto.CoilDetailDto;
import com.example.steelorder.dto.InventorySearchForm;
import com.example.steelorder.dto.SteelStockSummaryDto;
import com.example.steelorder.entity.Coil;
import com.example.steelorder.entity.SteelSheet;
import com.example.steelorder.entity.enums.CoilStatus;
import com.example.steelorder.repository.CoilRepository;
import com.example.steelorder.repository.SteelSheetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class InventoryServiceIntegrationTest {

    private static final AtomicInteger TEST_SEQ = new AtomicInteger(5000);

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private SteelSheetRepository steelSheetRepository;

    @Autowired
    private CoilRepository coilRepository;

    @Test
    @DisplayName("統合: search(null) は在庫ステータス境界（0/安全在庫未満/以上）を正しく判定する")
    void search_evaluatesStockStatusByBoundary() {
        SteelSheet outOfStock = createSheet("INV-OUT", "SPCC", "家電", new BigDecimal("1.00"), 1000, 10000);
        SteelSheet lowStock = createSheet("INV-LOW", "SPCC", "家電", new BigDecimal("1.00"), 1000, 10000);
        SteelSheet enough = createSheet("INV-ENOUGH", "SPCC", "家電", new BigDecimal("1.00"), 1000, 10000);

        createCoil(outOfStock, "CL-OUT", 3000, 3000, 0, CoilStatus.FULLY_ALLOCATED, LocalDate.now().minusDays(10));
        createCoil(lowStock, "CL-LOW", 10000, 10000, 9999, CoilStatus.PARTIALLY_ALLOCATED, LocalDate.now().minusDays(8));
        createCoil(enough, "CL-ENOUGH", 10000, 10000, 10000, CoilStatus.AVAILABLE, LocalDate.now().minusDays(7));

        List<SteelStockSummaryDto> result = inventoryService.search(null);
        Map<String, SteelStockSummaryDto> byCode = result.stream()
                .filter(s -> s.steelCode().startsWith("INV-"))
                .collect(Collectors.toMap(SteelStockSummaryDto::steelCode, Function.identity()));

        assertThat(byCode.get(outOfStock.getSteelCode()).stockStatusLabel()).isEqualTo("在庫切れ");
        assertThat(byCode.get(lowStock.getSteelCode()).stockStatusLabel()).isEqualTo("要発注");
        assertThat(byCode.get(enough.getSteelCode()).stockStatusLabel()).isEqualTo("適正");
    }

    @Test
    @DisplayName("統合: 検索条件（鋼種/用途/板厚/板幅）で一致する鋼板のみ抽出される")
    void search_filtersByAllCriteria() {
        SteelSheet match = createSheet("INV-MATCH", "JAC270C", "自動車外板", new BigDecimal("1.40"), 1500, 12000);
        SteelSheet other = createSheet("INV-OTHER", "SPCC", "家電", new BigDecimal("0.80"), 900, 10000);

        createCoil(match, "CL-MATCH", 8000, 8000, 8000, CoilStatus.AVAILABLE, LocalDate.now().minusDays(3));
        createCoil(other, "CL-OTHER", 8000, 8000, 8000, CoilStatus.AVAILABLE, LocalDate.now().minusDays(3));

        InventorySearchForm form = new InventorySearchForm(
                "JAC",
                "自動車",
                new BigDecimal("1.20"),
                new BigDecimal("1.50"),
                1200,
                1600
        );

        List<SteelStockSummaryDto> result = inventoryService.search(form);
        List<SteelStockSummaryDto> ours = result.stream()
                .filter(s -> s.steelCode().startsWith("INV-"))
                .toList();

        assertThat(ours).hasSize(1);
        assertThat(ours.getFirst().steelCode()).isEqualTo(match.getSteelCode());
    }

    @Test
    @DisplayName("統合: DEPLETED コイルは集計対象外となり、長期滞留判定は90日境界を含む")
    void search_excludesDepletedAndEvaluatesLongTermBoundary() {
        SteelSheet sheet = createSheet("INV-DETAIL", "SGCC", "家電", new BigDecimal("0.80"), 1219, 5000);

        createCoil(sheet, "CL-DETAIL-A", 4000, 4000, 4000, CoilStatus.AVAILABLE, LocalDate.now().minusDays(90));
        createCoil(sheet, "CL-DETAIL-B", 3000, 3000, 3000, CoilStatus.AVAILABLE, LocalDate.now().minusDays(20));
        createCoil(sheet, "CL-DETAIL-DEP", 9999, 9999, 0, CoilStatus.DEPLETED, LocalDate.now().minusDays(200));

        List<SteelStockSummaryDto> result = inventoryService.search(new InventorySearchForm(null, null, null, null, null, null));
        SteelStockSummaryDto summary = result.stream()
                .filter(s -> s.steelCode().equals(sheet.getSteelCode()))
                .findFirst()
                .orElseThrow();

        assertThat(summary.totalPhysicalWeight()).isEqualTo(7000);
        assertThat(summary.totalAvailableWeight()).isEqualTo(7000);
        assertThat(summary.coils()).hasSize(2);

        Map<String, CoilDetailDto> byNumber = summary.coils().stream()
                .collect(Collectors.toMap(CoilDetailDto::coilNumber, Function.identity()));
        assertThat(byNumber.get("CL-DETAIL-A").isLongTermStagnant()).isTrue();
        assertThat(byNumber.get("CL-DETAIL-B").isLongTermStagnant()).isFalse();
    }

    private SteelSheet createSheet(String codePrefix,
                                   String grade,
                                   String usage,
                                   BigDecimal thickness,
                                   int width,
                                   int safetyStock) {
        int seq = TEST_SEQ.incrementAndGet();
        SteelSheet sheet = new SteelSheet();
        sheet.setSteelCode(codePrefix + "-" + seq);
        sheet.setSteelName("在庫テスト鋼板-" + seq);
        sheet.setSteelGrade(grade);
        sheet.setUsageCategory(usage);
        sheet.setThickness(thickness);
        sheet.setWidth(width);
        sheet.setSafetyStockWeight(safetyStock);
        return steelSheetRepository.saveAndFlush(sheet);
    }

    private Coil createCoil(SteelSheet sheet,
                            String coilNumber,
                            int initialWeight,
                            int physicalWeight,
                            int availableWeight,
                            CoilStatus status,
                            LocalDate arrivalDate) {
        Coil coil = new Coil();
        coil.setCoilNumber(coilNumber);
        coil.setSteelSheet(sheet);
        coil.setInitialWeight(initialWeight);
        coil.setPhysicalWeight(physicalWeight);
        coil.setAvailableWeight(availableWeight);
        coil.setStatus(status);
        coil.setArrivalDate(arrivalDate);
        return coilRepository.saveAndFlush(coil);
    }
}
