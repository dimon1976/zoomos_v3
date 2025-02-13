package by.zoomos.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.ui.Model;

/**
 * Контроллер для веб-интерфейса
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class WebController {

    @GetMapping("/")
    public String index() {
        return "redirect:/upload";
    }

    @GetMapping("/upload")
    public String uploadPage() {
        return "upload/index";
    }

    @GetMapping("/status")
    public String statusPage(@RequestParam(required = false) Long id, Model model) {
        if (id != null) {
            model.addAttribute("statusId", id);
        }
        return "status/index";
    }

    @GetMapping("/results")
    public String resultsPage() {
        return "results/index";
    }

    @GetMapping("/mapping")
    public String mappingPage() {
        return "mapping/index";
    }

    @GetMapping("/clients")
    public String clientsPage() {
        return "clients/index";
    }

    @GetMapping("/export")
    public String exportPage() {
        return "export/index";
    }
}