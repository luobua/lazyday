package com.fan.lazyday.application.service.impl;

import com.fan.lazyday.application.service.AuthService;
import com.fan.lazyday.domain.user.entity.UserEntity;
import com.fan.lazyday.domain.user.po.User;
import com.fan.lazyday.domain.user.repository.UserRepository;
import com.fan.lazyday.infrastructure.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;

    @Override
    public Mono<User> createUser(String username, String email, String password, Long tenantId, String role) {
        return userRepository.findByUsername(username)
                .flatMap(existing -> Mono.<User>error(BizException.conflict("DUPLICATE_USERNAME", "用户名已被使用")))
                .switchIfEmpty(Mono.defer(() -> {
                    if (email != null && !email.isBlank()) {
                        return userRepository.findByEmail(email)
                                .flatMap(existing -> Mono.<User>error(BizException.conflict("DUPLICATE_EMAIL", "邮箱已被使用")))
                                .switchIfEmpty(Mono.defer(() -> doCreateUser(username, email, password, tenantId, role)));
                    }
                    return doCreateUser(username, email, password, tenantId, role);
                }));
    }

    private Mono<User> doCreateUser(String username, String email, String password, Long tenantId, String role) {
        return UserEntity.hashPassword(password)
                .flatMap(hash -> {
                    User user = new User();
                    user.setUsername(username);
                    user.setEmail(email);
                    user.setPasswordHash(hash);
                    user.setTenantId(tenantId);
                    user.setRole(role);
                    user.setStatus("ACTIVE");
                    return userRepository.insert(user);
                });
    }

    @Override
    public Mono<User> authenticate(String username, String password) {
        return userRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(BizException.unauthorized("INVALID_CREDENTIALS", "用户名或密码错误")))
                .flatMap(user -> {
                    if ("DISABLED".equals(user.getStatus())) {
                        return Mono.error(BizException.forbidden("ACCOUNT_DISABLED", "账户已禁用"));
                    }
                    UserEntity entity = UserEntity.fromPo(user);
                    return entity.verifyPassword(password)
                            .flatMap(matches -> {
                                if (!matches) {
                                    return Mono.error(BizException.unauthorized("INVALID_CREDENTIALS", "用户名或密码错误"));
                                }
                                return Mono.just(user);
                            });
                });
    }
}
