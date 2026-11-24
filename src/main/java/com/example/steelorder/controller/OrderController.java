package com.example.steelorder.controller;

import com.example.steelorder.dto.OrderForm;
import com.example.steelorder.entity.SteelSheet;
import com.example.steelorder.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/orders")
    public String orders(Model model) {
        model.addAttribute("orders", orderService.findAllOrders());

        List<SteelSheet> steelSheets = orderService.findSteelSheets();
        Map<Long, Integer> availableWeights = new HashMap<>();
        for (SteelSheet sheet : steelSheets) {
            availableWeights.put(sheet.getSteelSheetId(), orderService.getAvailableWeight(sheet));
        }

        model.addAttribute("steelSheets", steelSheets);
        model.addAttribute("availableWeights", availableWeights);
        model.addAttribute("activePage", "orders");
        model.addAttribute("pageTitle", "受注登録・注文一覧");

        if (!model.containsAttribute("orderForm")) {
            model.addAttribute("orderForm", new OrderForm("", null, null, LocalDate.now().plusDays(7)));
        }
        return "orders";
    }

    @PostMapping("/orders")
    public String register(@Valid @ModelAttribute("orderForm") OrderForm orderForm,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.orderForm", bindingResult);
            redirectAttributes.addFlashAttribute("orderForm", orderForm);
            redirectAttributes.addFlashAttribute("errorMessage", "入力内容を確認してください");
            return "redirect:/orders";
        }

        try {
            String message = orderService.registerOrder(orderForm);
            if (message.startsWith("在庫不足")) {
                redirectAttributes.addFlashAttribute("warningMessage", message);
            } else {
                redirectAttributes.addFlashAttribute("successMessage", message);
            }
        } catch (OptimisticLockingFailureException ex) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "他のユーザーと同時に注文が競合したため、在庫引当を中断しました。再度お試しください。");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/orders";
    }

    @PostMapping("/orders/{id}/cancel")
    public String cancel(@PathVariable("id") Long orderId,
                         RedirectAttributes redirectAttributes) {
        try {
            redirectAttributes.addFlashAttribute("successMessage", orderService.cancelOrder(orderId));
        } catch (IllegalStateException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (OptimisticLockingFailureException ex) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "他のユーザーと同時に注文が競合したため、処理を中断しました。再度お試しください。");
        }
        return "redirect:/orders";
    }
}
