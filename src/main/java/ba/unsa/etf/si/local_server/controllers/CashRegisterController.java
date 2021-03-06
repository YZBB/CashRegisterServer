package ba.unsa.etf.si.local_server.controllers;

import ba.unsa.etf.si.local_server.responses.CashRegisterResponse;
import ba.unsa.etf.si.local_server.responses.Response;
import ba.unsa.etf.si.local_server.services.CashRegisterService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

@AllArgsConstructor
@RestController
public class CashRegisterController {
    private final CashRegisterService cashRegisterService;

    @Secured("ROLE_OFFICEMAN")
    @PostMapping("/api/cash-register/register")
    public ResponseEntity<CashRegisterResponse> obtainIds() {
        CashRegisterResponse response = cashRegisterService.registerCashRegister();
        return ResponseEntity.ok(response);
    }

    @Secured({"ROLE_OFFICEMAN", "ROLE_CASHIER"})
    @GetMapping("/api/cash-register/data")
    public ResponseEntity<CashRegisterResponse> obtainCashRegisterData(@RequestParam(required = false, name = "cash_register_id") Long id) {
        CashRegisterResponse response = cashRegisterService.getCashRegisterData(id);
        return ResponseEntity.ok(response);
    }

}
