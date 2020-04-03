package ba.unsa.etf.si.local_server.services;

import ba.unsa.etf.si.local_server.exceptions.BadRequestException;
import ba.unsa.etf.si.local_server.exceptions.ResourceNotFoundException;
import ba.unsa.etf.si.local_server.models.transactions.*;
import ba.unsa.etf.si.local_server.repositories.ReceiptRepository;
import ba.unsa.etf.si.local_server.requests.ReceiptRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.validation.ConstraintViolationException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static ba.unsa.etf.si.local_server.models.transactions.ReceiptStatus.*;

@RequiredArgsConstructor
@Service
public class ReceiptService {
    private final ReceiptRepository receiptRepository;
    private final ProductService productService;
    private final MainReceiptService mainReceiptService;

    @Value("${main_server.office_id}")
    private long officeId;

    @Value("${main_server.business_id}")
    private long businessId;

    public String checkRequest(ReceiptRequest receiptRequest) {
        Set<ReceiptItem> items = receiptRequest
                    .getReceiptItems()
                    .stream()
                    .map(receiptItemRequest -> new ReceiptItem(
                            null,
                            receiptItemRequest.getId(),
                            receiptItemRequest.getQuantity()))
                    .collect(Collectors.toSet());

        String[] receiptIdData = receiptRequest.getReceiptId().split("-");
        Long now = Long.parseLong(receiptIdData[3]);

        PaymentMethod paymentMethod;

        try {
            paymentMethod = Enum.valueOf(PaymentMethod.class, receiptRequest.getPaymentMethod());
        } catch (NullPointerException | IllegalArgumentException err) {
            throw new BadRequestException("Illegal payment method");
        }

        ReceiptStatus receiptStatus = paymentMethod == PaymentMethod.PAY_APP ? PENDING : PAID;

        Receipt receipt = new Receipt(
                null,
                receiptRequest.getReceiptId(),
                receiptStatus,
                paymentMethod,
                receiptRequest.getCashRegisterId(),
                officeId,
                businessId,
                items,
                receiptRequest.getUsername(),
                getTotalPrice(items),
                now
        );

        items.forEach(receiptItem ->
            productService.updateProductQuantity(receiptItem.getProductId(), -1 * receiptItem.getQuantity())
        );


        receiptRepository.save(receipt);
        mainReceiptService.postReceiptToMain(receipt);

        if(receipt.getReceiptStatus() == PENDING) {
            mainReceiptService.pollReceiptStatus(receipt.getReceiptId());
        }

        return "Successfully created receipt";
    }

    public String reverseReceipt(String id) {
        Receipt receipt = getReceipt(id);

        if (receipt.getReceiptStatus() == DELETED) {
            return "Cannot reverse receipt reversion";
        }

        Receipt reversedReceipt = getReversedReceipt(receipt);

        reversedReceipt
                .getReceiptItems()
                .forEach(receiptItem ->
                        productService.updateProductQuantity(receiptItem.getProductId(), -1 * receiptItem.getQuantity())
                );

        try {
            receiptRepository.save(reversedReceipt);
            mainReceiptService.postReceiptToMain(reversedReceipt);
        } catch (ConstraintViolationException err) {
            return "Cannot reverse already reversed receipt";
        }

        return "Receipt successfully reversed!";
    }

    private Receipt getReversedReceipt(Receipt receipt) {
        BigDecimal reversedTotalPrice = receipt.getTotalPrice().multiply(BigDecimal.valueOf(-1));

        Set<ReceiptItem> items = receipt
                .getReceiptItems()
                .stream()
                .map(item -> new ReceiptItem(null, item.getProductId(), item.getQuantity() * -1))
                .collect(Collectors.toSet());

        return new Receipt(
                null,
                "-" + receipt.getReceiptId(),
                DELETED,
                receipt.getPaymentMethod(),
                receipt.getCashRegisterId(),
                receipt.getOfficeId(),
                receipt.getBusinessId(),
                items,
                receipt.getUsername(),
                reversedTotalPrice,
                new Date().getTime()
        );
    }

    public void updateReceiptStatus(String id, ReceiptStatus receiptStatus) {
        Receipt receipt = getReceipt(id);
        receipt.setReceiptStatus(receiptStatus);
        receiptRepository.save(receipt);
    }

    public Receipt getReceipt(String id) {
        return receiptRepository
                .findByReceiptId(id)
                .orElseThrow(() -> new ResourceNotFoundException("No such receipt!"));
    }

    private BigDecimal getTotalPrice(Set<ReceiptItem> items){
        return items
                .stream()
                .map(item ->
                        productService
                                .getProduct(item.getProductId())
                                .getPrice()
                                .multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<Receipt> getReceipts(Long cashRegisterId) {
        return receiptRepository.findByCashRegisterId(cashRegisterId);
    }
}
