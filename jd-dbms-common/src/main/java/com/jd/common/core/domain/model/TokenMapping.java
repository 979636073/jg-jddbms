package com.jd.common.core.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * TODO
 *
 * @author: shawn
 * @Date: 2023/12/21 15:33
 * @Version 1.0.0
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TokenMapping implements Serializable {
    private static final long serialVersionUID = 1L;

    private  String visitToken;
    private  String innerToken;
    private  String ssoToken;

}
