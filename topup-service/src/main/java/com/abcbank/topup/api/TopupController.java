package com.abcbank.topup.api;

import com.abcbank.topup.api.models.*;
import com.abcbank.topup.stores.VoucherStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.async.WebAsyncTask;

import javax.validation.Valid;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
public class TopupController {

    private static final long TIME_OUT = 3 * 1000L;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private TopupApi topupApi;

    @Autowired
    private VoucherStore voucherStore;

    @PostMapping(path = "/topup")
    public WebAsyncTask<TopupPurchaseResponse> purchase(@RequestHeader("username") String username, @Valid @RequestBody TopupPurchaseRequest request) {
        AtomicBoolean tooLong = new AtomicBoolean(false);
        WebAsyncTask<TopupPurchaseResponse> asyncTask = new WebAsyncTask<>(TIME_OUT, "asyncExecutor", () -> {
            VoucherData voucherData = topupApi.purchase(username, request);
            if (tooLong.get()) {
                sendSMSAsync(username, request, voucherData);
            }
            return new TopupPurchaseResponse("success", voucherData.getCode());
        });
        asyncTask.onTimeout(() -> {
            tooLong.set(true);
            return new TopupPurchaseResponse("Request is being processed within 30 seconds", "");
        });
        asyncTask.onError(() -> new TopupPurchaseResponse("Request error", ""));
        return asyncTask;
    }

    @Async
    private void sendSMSAsync(String username, TopupPurchaseRequest request, VoucherData voucherData) {
        //TODO-send voucher code via SMS
        System.out.println("Sending sms = " + voucherData.getCode());
    }

    @GetMapping(path = "/topup")
    public TopupGetVouchersResponse getVouchers(@RequestHeader("username") String username, @Valid @RequestBody TopupGetVouchersRequest request) {
        Collection<VoucherData> vouchers = voucherStore.getVouchers(username, request.getPhoneNumber());
        TopupGetVouchersResponse response = new TopupGetVouchersResponse();
        response.setPhoneNumber(request.getPhoneNumber());
        response.setVouchers(vouchers);
        return response;
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Map<String, String> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return errors;
    }
}
