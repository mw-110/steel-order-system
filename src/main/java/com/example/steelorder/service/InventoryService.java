package com.example.steelorder.service;

import com.example.steelorder.dto.CoilDetailDto;
import com.example.steelorder.dto.InventorySearchForm;
import com.example.steelorder.dto.SteelStockSummaryDto;
import com.example.steelorder.entity.Coil;
import com.example.steelorder.entity.SteelSheet;
import com.example.steelorder.entity.enums.CoilStatus;
import com.example.steelorder.repository.CoilRepository;
import com.example.steelorder.repository.SteelSheetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

@Service
public class InventoryService {

    private final SteelSheetRepository steelSheetRepository;
    private final CoilRepository coilRepository;

    public InventoryService(SteelSheetRepository steelSheetRepository,
                            CoilRepository coilRepository) {
        this.steelSheetRepository = steelSheetRepository;
        this.coilRepository = coilRepository;
    }

    @Transactional(readOnly = true)
    public List<SteelStockSummaryDto> search(InventorySearchForm form) {
        LocalDate today = LocalDate.now();

        return steelSheetRepository.findAll().stream()
                .filter(sheet -> matches(form, sheet))
                .map(sheet -> toSummary(sheet, today))
                .toList();
    }

    private boolean matches(InventorySearchForm form, SteelSheet sheet) {
        if (form == null) {
            return true;
        }

        if (StringUtils.hasText(form.steelGrade())
                && !sheet.getSteelGrade().contains(form.steelGrade().trim())) {
            return false;
        }
        if (StringUtils.hasText(form.usageCategory())
                && (sheet.getUsageCategory() == null
                || !sheet.getUsageCategory().contains(form.usageCategory().trim()))) {
            return false;
        }
        if (form.minThickness() != null
                && sheet.getThickness().compareTo(form.minThickness()) < 0) {
            return false;
        }
        if (form.maxThickness() != null
                && sheet.getThickness().compareTo(form.maxThickness()) > 0) {
            return false;
        }
        if (form.minWidth() != null && sheet.getWidth() < form.minWidth()) {
            return false;
        }
        return form.maxWidth() == null || sheet.getWidth() <= form.maxWidth();
    }

    private SteelStockSummaryDto toSummary(SteelSheet sheet, LocalDate today) {
        List<Coil> coils = coilRepository
                .findBySteelSheetAndStatusNotOrderByArrivalDateAsc(sheet, CoilStatus.DEPLETED);
        List<CoilDetailDto> coilDtos = coils.stream()
                .map(coil -> toCoilDetail(coil, today))
                .toList();

        int totalPhysicalWeight = coils.stream()
                .mapToInt(coil -> coil.getPhysicalWeight() == null ? 0 : coil.getPhysicalWeight())
                .sum();
        int totalAvailableWeight = coils.stream()
                .mapToInt(coil -> coil.getAvailableWeight() == null ? 0 : coil.getAvailableWeight())
                .sum();
        int totalAllocatedWeight = totalPhysicalWeight - totalAvailableWeight;

        String[] stockStatus = switch (totalAvailableWeight) {
            case 0 -> new String[]{"在庫切れ", "bg-danger"};
            default -> totalAvailableWeight < sheet.getSafetyStockWeight()
                    ? new String[]{"要発注", "bg-warning text-dark"}
                    : new String[]{"適正", "bg-success"};
        };

        return new SteelStockSummaryDto(
                sheet.getSteelSheetId(),
                sheet.getSteelCode(),
                sheet.getSteelName(),
                sheet.getSteelGrade(),
                sheet.getUsageCategory(),
                sheet.getThickness(),
                sheet.getWidth(),
                sheet.getSafetyStockWeight(),
                totalPhysicalWeight,
                totalAllocatedWeight,
                totalAvailableWeight,
                stockStatus[0],
                stockStatus[1],
                coilDtos
        );
    }

    private CoilDetailDto toCoilDetail(Coil coil, LocalDate today) {
        int initialWeight = coil.getInitialWeight() == null ? 0 : coil.getInitialWeight();
        int availableWeight = coil.getAvailableWeight() == null ? 0 : coil.getAvailableWeight();

        String statusLabel = switch (coil.getStatus()) {
            case AVAILABLE -> "在庫あり";
            case PARTIALLY_ALLOCATED -> "一部引当済";
            case FULLY_ALLOCATED -> "全量引当済";
            case DEPLETED -> "払出完了";
        };

        String badgeClass = switch (coil.getStatus()) {
            case AVAILABLE -> "bg-success";
            case PARTIALLY_ALLOCATED -> "bg-warning text-dark";
            case FULLY_ALLOCATED -> "bg-danger";
            case DEPLETED -> "bg-secondary";
        };

        boolean longTerm = coil.getArrivalDate() != null
                && !coil.getArrivalDate().isAfter(today.minusDays(90));

        return new CoilDetailDto(
                coil.getCoilId(),
                coil.getCoilNumber(),
                coil.getArrivalDate(),
                initialWeight,
                initialWeight - availableWeight,
                availableWeight,
                statusLabel,
                badgeClass,
                longTerm
        );
    }
}
