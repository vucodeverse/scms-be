package edu.fpt.sba.scms.mapper;

import edu.fpt.sba.scms.dto.response.UserDetailDto;
import edu.fpt.sba.scms.dto.response.UserDto;
import edu.fpt.sba.scms.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Entity ↔ DTO của user (IMPLEMENTATION.md §10.2.1). {@code branchName}/{@code roleCode}/{@code hourlyRate}
 * do service truy vấn sẵn (tránh N+1 và không lộ quan hệ lazy ra ngoài transaction).
 * <p>
 * {@code hasPin}/{@code hasCard}/{@code hasPassword} suy từ hash nhưng chỉ trả boolean, không trả hash.
 * {@code position} lấy từ {@code roleCode} do service truyền (mapper không được chạm {@code common.enums.Roles}).
 * để tránh vô tình map {@code pinHash}/{@code passwordHash} ra DTO.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "roleCode", source = "roleCode")
    @Mapping(target = "branchName", source = "branchName")
    @Mapping(target = "hourlyRate", source = "hourlyRate")
    @Mapping(target = "position", source = "roleCode")
    @Mapping(target = "hasPin", expression = "java(user.getPinHash() != null)")
    @Mapping(target = "hasCard", expression = "java(user.getCardCode() != null)")
    UserDto toDto(User user, String roleCode, String branchName, Long hourlyRate);

    @Mapping(target = "roleCode", source = "roleCode")
    @Mapping(target = "branchName", source = "branchName")
    @Mapping(target = "hourlyRate", source = "hourlyRate")
    @Mapping(target = "position", source = "roleCode")
    @Mapping(target = "hasPin", expression = "java(user.getPinHash() != null)")
    @Mapping(target = "hasCard", expression = "java(user.getCardCode() != null)")
    @Mapping(target = "hasPassword", expression = "java(user.getPasswordHash() != null)")
    UserDetailDto toDetail(User user, String roleCode, String branchName, Long hourlyRate);
}
