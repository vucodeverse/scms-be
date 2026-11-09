package edu.fpt.sba.scms.mapper;

import edu.fpt.sba.scms.dto.response.BranchDetailDto;
import edu.fpt.sba.scms.dto.response.BranchDto;
import edu.fpt.sba.scms.dto.response.UserDto;
import edu.fpt.sba.scms.model.Branch;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/** Entity ↔ DTO của chi nhánh (IMPLEMENTATION.md §10.3). Số liệu tổng hợp do service tính sẵn. */
@Mapper(componentModel = "spring")
public interface BranchMapper {

    @Mapping(target = "managerUserId", source = "branch.managerId")
    @Mapping(target = "managerName", source = "managerName")
    @Mapping(target = "employeeCount", source = "employeeCount")
    @Mapping(target = "posOnline", expression = "java(null)")
    BranchDto toDto(Branch branch, String managerName, long employeeCount);

    @Mapping(target = "managerUserId", source = "branch.managerId")
    @Mapping(target = "managerName", source = "managerName")
    @Mapping(target = "employeeCount", source = "employeeCount")
    @Mapping(target = "posOnline", expression = "java(null)")
    @Mapping(target = "employees", source = "employees")
    BranchDetailDto toDetail(Branch branch, String managerName, long employeeCount, List<UserDto> employees);
}
