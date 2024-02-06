package com.hmall.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collection;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CartDTO implements java.io.Serializable {
    private Long userId;
    private Collection<Long> itemIds;
}
