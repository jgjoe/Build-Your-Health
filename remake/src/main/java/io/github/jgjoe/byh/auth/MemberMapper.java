package io.github.jgjoe.byh.auth;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** SQL lives in {@code mapper/MemberMapper.xml}. */
@Mapper
public interface MemberMapper {

    Member findById(@Param("id") String id);
}
