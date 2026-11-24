package com.example.steelorder.controller;

import com.example.steelorder.dto.ArrivalForm;
import com.example.steelorder.service.ArrivalService;
import com.example.steelorder.service.ArrivalService.ArrivalResult;
import com.example.steelorder.service.ArrivalService.ResultLevel;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ArrivalController {

    private final ArrivalService arrivalService;

    public ArrivalController(ArrivalService arrivalService) {
        this.arrivalService = arrivalService;
    }

    @GetMapping("/arrivals")
    public String arrivals(@RequestParam(name = "steelSheetId", required = false) Long steelSheetId,
                           Model model) {
        model.addAttribute("arrivalOptions", arrivalService.getSteelArrivalOptions());
        model.addAttribute("histories", arrivalService.getRecentArrivals());
        model.addAttribute("activePage", "arrivals");
        model.addAttribute("pageTitle", "コイル入荷登録");

        if (!model.containsAttribute("arrivalForm")) {
            model.addAttribute("arrivalForm", arrivalService.buildDefaultForm(steelSheetId));
        }
        return "arrivals";
    }

    @PostMapping("/arrivals")
    public String register(@Valid @ModelAttribute("arrivalForm") ArrivalForm arrivalForm,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.arrivalForm", bindingResult);
            redirectAttributes.addFlashAttribute("arrivalForm", arrivalForm);
            redirectAttributes.addFlashAttribute("errorMessage", "入力内容を確認してください");
            return "redirect:/arrivals";
        }

        try {
            ArrivalResult result = arrivalService.registerArrival(arrivalForm);
            if (result.level() == ResultLevel.SUCCESS) {
                redirectAttributes.addFlashAttribute("successMessage", result.message());
            } else {
                redirectAttributes.addFlashAttribute("infoMessage", result.message());
            }
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            redirectAttributes.addFlashAttribute("arrivalForm", arrivalForm);
            return "redirect:/arrivals?steelSheetId=" + arrivalForm.steelSheetId();
        }

        return "redirect:/arrivals";
    }
}
