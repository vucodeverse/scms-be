package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.dto.request.ApplyVoucherRequest;
import edu.fpt.sba.scms.dto.request.CancelOrderRequest;
import edu.fpt.sba.scms.dto.request.CreateOrderRequest;
import edu.fpt.sba.scms.dto.request.query.OrderListQuery;
import edu.fpt.sba.scms.dto.response.OrderDetailDto;
import edu.fpt.sba.scms.dto.response.OrderDto;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import org.springframework.data.domain.Page;

/**
 * Service quản lý vòng đời đơn hàng POS: tạo đơn, tra cứu, áp voucher, và hủy đơn (UC16, UC17, UC18, BR-26b).
 */
public interface OrderService {

    OrderDetailDto createOrder(CreateOrderRequest request, UserPrincipal principal);

    Page<OrderDto> listOrders(OrderListQuery query);

    OrderDetailDto getOrderById(Integer id);

    OrderDetailDto applyVoucher(Integer id, ApplyVoucherRequest request);

    OrderDetailDto removeVoucher(Integer id);

    OrderDto cancelOrder(Integer id, CancelOrderRequest request, UserPrincipal principal);
}
