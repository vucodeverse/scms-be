package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.dto.ApiErrorResponse;
import edu.fpt.sba.scms.dto.PageResponse;
import edu.fpt.sba.scms.dto.request.ApplyVoucherRequest;
import edu.fpt.sba.scms.dto.request.CancelOrderRequest;
import edu.fpt.sba.scms.dto.request.CreateOrderRequest;
import edu.fpt.sba.scms.dto.request.query.OrderListQuery;
import edu.fpt.sba.scms.dto.response.OrderDetailDto;
import edu.fpt.sba.scms.dto.response.OrderDto;
import edu.fpt.sba.scms.dto.response.ReceiptResponse;
import edu.fpt.sba.scms.dto.response.report.ReportFileDto;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.OrderService;
import edu.fpt.sba.scms.service.ReceiptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Controller phục vụ Quản lý đơn hàng POS tại quầy (UC16, UC17, UC18, BR-26b).
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "10. POS Orders", description = "Quản lý tạo đơn, tra cứu, áp voucher và hủy đơn POS (UC16, UC17, UC18, BR-26b)")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    private final OrderService orderService;
    private final ReceiptService receiptService;

    @Operation(summary = "Tạo đơn hàng mới tại quầy POS (UC16)",
            description = "Thu ngân chọn món, size, đường đá, topping và ghi chú để tạo đơn hàng mới ở trạng thái PENDING.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tạo đơn hàng thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu gửi lên không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Sản phẩm không khả dụng hoặc vi phạm quy tắc",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER', 'STAFF')")
    public OrderDetailDto createOrder(
            @RequestBody @Valid CreateOrderRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return orderService.createOrder(request, principal);
    }

    @Operation(summary = "Tra cứu danh sách đơn hàng POS (UC18)",
            description = "Lọc đơn hàng theo chi nhánh, trạng thái, khoảng ngày giờ, từ khóa hóa đơn và phân trang. Thu ngân chỉ xem đơn thuộc ca hiện tại đang mở; Quản lý / COO xem toàn bộ.")
    @GetMapping
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER', 'STAFF')")
    public PageResponse<OrderDto> listOrders(
            @ModelAttribute @Valid OrderListQuery query,
            @AuthenticationPrincipal UserPrincipal principal) {
        return PageResponse.of(orderService.listOrders(query, principal));
    }

    @Operation(summary = "Xem chi tiết một đơn hàng theo ID (UC18)",
            description = "Trả về thông tin chi tiết đơn hàng kèm danh sách dòng món, topping và voucher áp dụng.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy chi tiết đơn hàng thành công"),
            @ApiResponse(responseCode = "403", description = "Thu ngân không có quyền xem đơn thuộc ca trước",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy đơn hàng",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER', 'STAFF')")
    public OrderDetailDto getOrderById(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return orderService.getOrderById(id, principal);
    }

    @Operation(summary = "Áp dụng mã khuyến mãi / voucher cho đơn hàng (UC17)",
            description = "Kiểm tra điều kiện voucher (thời hạn, giá trị tối thiểu, lượt dùng) và áp dụng giảm giá vào đơn hàng (BR-21, BR-22).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Áp dụng voucher thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy đơn hàng hoặc voucher",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Voucher hết hạn, chưa đạt giá trị tối thiểu hoặc đơn đã có voucher",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PostMapping("/{id}/voucher")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER', 'STAFF')")
    public OrderDetailDto applyVoucher(
            @PathVariable Integer id,
            @RequestBody @Valid ApplyVoucherRequest request) {
        return orderService.applyVoucher(id, request);
    }

    @Operation(summary = "Gỡ bỏ voucher khỏi đơn hàng (UC17)",
            description = "Hủy mã voucher đã áp dụng và khôi phục lại giá trị thanh toán gốc của đơn hàng.")
    @DeleteMapping("/{id}/voucher")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER', 'STAFF')")
    public OrderDetailDto removeVoucher(@PathVariable Integer id) {
        return orderService.removeVoucher(id);
    }

    @Operation(summary = "Hủy đơn hàng chưa thanh toán (BR-26b)",
            description = "Thu ngân hủy đơn hàng ở trạng thái PENDING với lý do hủy bắt buộc. Không áp dụng cho đơn đã PAID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Hủy đơn hàng thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy đơn hàng",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Đơn hàng đã thanh toán hoặc thiếu lý do hủy",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER', 'STAFF')")
    public OrderDto cancelOrder(
            @PathVariable Integer id,
            @RequestBody @Valid CancelOrderRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return orderService.cancelOrder(id, request, principal);
    }

    @Operation(summary = "Xem dữ liệu biên lai thanh toán (UC22, BR-31)",
            description = "Lấy thông tin chi tiết hóa đơn thanh toán của đơn hàng đã PAID để hiển thị form mẫu trên quầy POS.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy thông tin biên lai thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy đơn hàng",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Đơn hàng chưa thanh toán (BR-31)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @GetMapping("/{id}/receipt")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER')")
    public ReceiptResponse getReceipt(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return receiptService.getReceipt(id, principal);
    }

    @Operation(summary = "Xuất file PDF biên lai thanh toán khổ K80 (UC22, BR-31)",
            description = "Tải file PDF hóa đơn thanh toán chuẩn in nhiệt K80 (80mm) theo mẫu.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tải file PDF thành công",
                    content = @Content(mediaType = "application/pdf")),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy đơn hàng",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Đơn hàng chưa thanh toán (BR-31)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @GetMapping("/{id}/receipt/export")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER')")
    public ResponseEntity<byte[]> exportReceiptPdf(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserPrincipal principal) {
        ReportFileDto file = receiptService.exportReceiptPdf(id, principal);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(file.content().length)
                .body(file.content());
    }

    @Operation(summary = "Gửi lệnh in biên lai tới máy in nhiệt ESC/POS (UC22, BR-31)",
            description = "In hoặc in lại (Reprint) hóa đơn đơn hàng qua máy in nhiệt quầy thu ngân và ghi nhận thời điểm in.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Gửi lệnh in thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy đơn hàng",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Đơn hàng chưa thanh toán (BR-31)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PostMapping("/{id}/receipt/print")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER')")
    public ReceiptResponse printReceipt(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return receiptService.printReceipt(id, principal);
    }
}
