package com.example.steelorder.service;

import com.example.steelorder.dto.DashboardAlertDto;
import com.example.steelorder.dto.DashboardSummaryDto;
import com.example.steelorder.entity.Coil;
import com.example.steelorder.entity.Order;
import com.example.steelorder.entity.SteelSheet;
import com.example.steelorder.entity.enums.OrderStatus;
import com.example.steelorder.repository.CoilRepository;
import com.example.steelorder.repository.OrderRepository;
import com.example.steelorder.repository.SteelSheetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class DashboardService {

    private final SteelSheetRepository steelSheetRepository;
    private final CoilRepository coilRepository;
    private final OrderRepository orderRepository;

    public DashboardService(SteelSheetRepository steelSheetRepository,
                            CoilRepository coilRepository,
                            OrderRepository orderRepository) {
        this.steelSheetRepository = steelSheetRepository;
        this.coilRepository = coilRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryDto getSummary() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.atTime(LocalTime.MAX);

        long todayOrders = orderRepository.countByOrderedAtBetween(start, end);
        long backordered = orderRepository.countByStatus(OrderStatus.BACKORDERED);
        long allocated = orderRepository.countByStatus(OrderStatus.ALLOCATED);
        return new DashboardSummaryDto(todayOrders, backordered, allocated);
    }

    @Transactional(readOnly = true)
    public List<DashboardAlertDto> getAlerts() {
        LocalDate today = LocalDate.now();
        List<DashboardAlertDto> alerts = new ArrayList<>();

        List<Coil> coils = coilRepository.findAll();

        for (SteelSheet sheet : steelSheetRepository.findAll()) {
            int available = coils.stream()
                    .filter(coil -> coil.getSteelSheet().getSteelSheetId().equals(sheet.getSteelSheetId()))
                    .mapToInt(coil -> coil.getAvailableWeight() == null ? 0 : coil.getAvailableWeight())
                    .sum();

            if (available == 0) {
                alerts.add(new DashboardAlertDto("DANGER",
                        sheet.getSteelName() + "（" + sheet.getSteelCode() + "）の有効在庫がゼロです",
                        "/arrivals", "入荷登録へ"));
            } else if (available < sheet.getSafetyStockWeight()) {
                alerts.add(new DashboardAlertDto("WARNING",
                        sheet.getSteelName() + "（" + sheet.getSteelCode() + "）の有効在庫が安全在庫を下回っています：" + available + "kg",
                        "/inventories", "在庫を確認"));
            }
        }

        for (Order order : orderRepository.findByStatus(OrderStatus.BACKORDERED)) {
            alerts.add(new DashboardAlertDto("DANGER",
                    "バックオーダー滞留：" + order.getOrderNumber() + "（" + order.getCustomerName() + "）",
                    "/orders", "注文を確認"));
        }

        for (Order order : orderRepository.findByStatus(OrderStatus.ALLOCATED)) {
            LocalDate delivery = order.getDesiredDeliveryDate();
            if (delivery.isBefore(today) || delivery.isEqual(today)) {
                alerts.add(new DashboardAlertDto("DANGER",
                        "本日納期の未出荷：" + order.getOrderNumber() + "（納期 " + delivery + "）",
                        "/shipments", "出荷管理へ"));
            } else if (!delivery.isAfter(today.plusDays(3)) && delivery.isAfter(today)) {
                alerts.add(new DashboardAlertDto("WARNING",
                        "納期接近の未出荷：" + order.getOrderNumber() + "（納期 " + delivery + "）",
                        "/shipments", "出荷準備へ"));
            }
        }

        boolean hasLongStoredCoil = coilRepository.findByAvailableWeightGreaterThan(0).stream()
                .anyMatch(coil -> !coil.getArrivalDate().isAfter(today.minusDays(90)));
        if (hasLongStoredCoil) {
            alerts.add(new DashboardAlertDto("INFO",
                    "長期滞留コイルがあります（入荷から90日以上経過）",
                    "/inventories", "コイルを確認"));
        }

        return alerts;
    }
}
