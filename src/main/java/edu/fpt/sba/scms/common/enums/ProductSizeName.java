package edu.fpt.sba.scms.common.enums;

/**
 * Size cố định của sản phẩm (IMPLEMENTATION.md §10.7: {@code S|M|L}).
 * Mỗi sản phẩm có tối đa một giá cho mỗi size — ràng buộc nằm ở unique index.
 */
public enum ProductSizeName {
    S,
    M,
    L
}
