package com.fayu.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fayu.message.domain.po.OffMessageBox;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户离线消息收件箱 Mapper
 */
@Mapper
public interface OffMessageBoxMapper extends BaseMapper<OffMessageBox> {

}