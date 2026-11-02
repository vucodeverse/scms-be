-- H2 (profile test) không có entity cho bảng này; môi trường thật tạo bảng bằng migration.
-- D chỉ đọc: UC12 trả công thức của sản phẩm (rỗng trong môi trường test).
create table if not exists recipe (
    recipe_id     int auto_increment primary key,
    product_id    int not null,
    ingredient_id int not null,
    quantity_base decimal(19, 4) not null,
    size          varchar(5)
);
