package com.intelliatech.app.service;

import com.intelliatech.app.dto.response.IrpAuthenticationToken;
import com.intelliatech.app.entity.IrpEnvironment;

public interface IrpAuthenticationService {
    IrpAuthenticationToken currentToken();
    IrpAuthenticationToken currentToken(IrpEnvironment environment);
    IrpAuthenticationToken refreshToken();
    IrpAuthenticationToken refreshToken(IrpEnvironment environment);
    void invalidateToken();
}
