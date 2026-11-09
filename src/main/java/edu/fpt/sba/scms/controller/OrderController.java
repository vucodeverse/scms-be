package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.dto.ApiErrorResponse;
import edu.fpt.sba.scms.dto.PageResponse;
import edu.fpt.sba.scms.dto.request.ApplyVoucherRequest;
import edu.fpt.sba.scms.dto.request.CancelOrderRequest;
import edu.fpt.sba.scms.dto.request.CreateOrderRequest;
import edu.fpt.sba.scms.dto.request.query.OrderListQuery;
import edu.fpt.sba.scms.dto.response.OrderDetailDto;
import edu.fpt.sba.scms.dto.response.OrderDto;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
            description = "Lọc đơn hàng theo chi nhánh, trạng thái, khoảng ngày giờ, từ khóa hóa đơn và phân trang.")
    @GetMapping
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER')")
    public PageResponse<OrderDto> listOrders(@ModelAttribute @Valid OrderListQuery query) {
        return PageResponse.of(orderService.listOrders(query));
    }

    @Operation(summary = "Xem chi tiết một đơn hàng theo ID (UC18)",
            description = "Trả về thông tin chi tiết đơn hàng kèm danh sách dòng món, topping và voucher áp dụng.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy chi tiết đơn hàng thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy đơn hàng",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER')")
    public OrderDetailDto getOrderById(@PathVariable Integer id) {
        return orderService.getOrderById(id);
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
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER')")
    public OrderDetailDto applyVoucher(
            @PathVariable Integer id,
            @RequestBody @Valid ApplyVoucherRequest request) {
        return orderService.applyVoucher(id, request);
    }

    @Operation(summary = "Gỡ bỏ voucher khỏi đơn hàng (UC17)",
            description = "Hủy mã voucher đã áp dụng và khôi phục lại giá trị thanh toán gốc của đơn hàng.")
    @DeleteMapping("/{id}/voucher")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER')")
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
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER')")
    public OrderDto cancelOrder(
            @PathVariable Integer id,
            @RequestBody @Valid CancelOrderRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return orderService.cancelOrder(id, request, principal);
    }
}
