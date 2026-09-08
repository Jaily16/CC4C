package com.cc4c.identity.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cc4c.identity.IdentityDtos.AdministratorPasswordRequest;
import com.cc4c.identity.IdentityDtos.ChangePasswordRequest;
import com.cc4c.identity.IdentityDtos.RegisterRequest;
import com.cc4c.identity.IdentityDtos.ResetPasswordRequest;
import com.cc4c.identity.IdentityDtos.UserResponse;
import com.cc4c.identity.IdentityDtos.UserUpdateRequest;
import com.cc4c.identity.IdentityDtos.VerificationPurpose;
import com.cc4c.identity.api.AccountRole;
import com.cc4c.identity.api.CurrentActor;
import com.cc4c.identity.api.IdentityLookup;
import com.cc4c.identity.api.IdentityNotificationLookup;
import com.cc4c.identity.api.NotificationContact;
import com.cc4c.identity.api.UserSnapshot;
import com.cc4c.shared.BusinessCode;
import com.cc4c.shared.BusinessException;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * IdentityService 协调 CC4C 的一项运行职责，并保持现有外部行为不变。
 */
@Service
public class IdentityService implements IdentityLookup, IdentityNotificationLookup {
    private final UserMapper userMapper;
    private final AdministratorMapper administratorMapper;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeService verificationCodeService;
    private final CurrentActor currentActor;
    private final SessionRevocationService sessionRevocationService;

    /**
     * 创建 IdentityService 并保存其必需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param userMapper 调用方提供的 {@code userMapper} 值
     * @param administratorMapper 调用方提供的 {@code administratorMapper} 值
     * @param passwordEncoder 调用方提供的 {@code passwordEncoder} 值
     * @param verificationCodeService 由容器注入的 VerificationCodeService 协作组件
     * @param currentActor 调用方提供的 {@code currentActor} 值
     * @param sessionRevocationService 由容器注入的 SessionRevocationService 协作组件
     */
    IdentityService(
            UserMapper userMapper,
            AdministratorMapper administratorMapper,
            PasswordEncoder passwordEncoder,
            VerificationCodeService verificationCodeService,
            CurrentActor currentActor,
            SessionRevocationService sessionRevocationService) {
        this.userMapper = userMapper;
        this.administratorMapper = administratorMapper;
        this.passwordEncoder = passwordEncoder;
        this.verificationCodeService = verificationCodeService;
        this.currentActor = currentActor;
        this.sessionRevocationService = sessionRevocationService;
    }

    /**
     * 变更 IdentityService 对应状态，并维持既有校验、事务及外部副作用边界。
     *
     * @param request 已经过声明式校验的接口请求体
     * @return 当前条件是否成立
     */
    @Transactional
    public boolean register(RegisterRequest request) {
        PasswordPolicy.requireWritable(request.password());
        verificationCodeService.consume(request.email(), VerificationPurpose.REGISTER, request.verificationCode());
        if (userMapper.exists(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getName, request.name()))) {
            throw new BusinessException(HttpStatus.CONFLICT, BusinessCode.REGISTER_FAIL, "用户名重复");
        }
        if (userMapper.exists(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getEmail, request.email()))) {
            throw new BusinessException(HttpStatus.CONFLICT, BusinessCode.REGISTER_FAIL, "该邮箱已经被注册");
        }

        UserEntity user = new UserEntity();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setMajor(request.major());
        user.setLanguage(request.language());
        user.setAvatar(request.avatar());
        user.setState(0);
        user.setTime(new Date());
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(HttpStatus.CONFLICT, BusinessCode.REGISTER_FAIL, "用户名或邮箱已存在");
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    BusinessCode.FOREIGN_KEY_CONSTRAINT_VIOLATION,
                    "Invalid favourite language");
        }
        return true;
    }

    /**
     * 执行 IdentityService 中的 authenticationAccount 职责，并保持既有权限、事务与副作用边界。
     *
     * @param role 调用方提供的 {@code role} 值
     * @param identifier 调用方提供的 {@code identifier} 值
     * @return 存在时包含目标值，否则为空
     */
    public Optional<AuthenticationAccount> authenticationAccount(AccountRole role, String identifier) {
        if (role == AccountRole.USER) {
            UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                    .eq(UserEntity::getEmail, identifier.trim().toLowerCase(java.util.Locale.ROOT))
                    .eq(UserEntity::getState, 0));
            return Optional.ofNullable(user)
                    .map(value -> new AuthenticationAccount(
                            Long.toString(value.getId()), value.getName(), value.getPassword()));
        }
        AdministratorEntity administrator = administratorMapper.selectById(identifier);
        return Optional.ofNullable(administrator)
                .map(value ->
                        new AuthenticationAccount(value.getAdminId(), value.getAdminId(), value.getAdminPassword()));
    }

    /**
     * 执行 IdentityService 中的 currentUser 职责，并保持既有权限、事务与副作用边界。
     *
     * @return 按当前声明计算、查询或转换得到的结果
     */
    public UserResponse currentUser() {
        return toResponse(requiredUser(currentActor.requiredUserId()));
    }

    /**
     * 变更 IdentityService 对应状态，并维持既有校验、事务及外部副作用边界。
     *
     * @param request 已经过声明式校验的接口请求体
     * @return 当前条件是否成立
     */
    @Transactional
    public boolean update(UserUpdateRequest request) {
        UserEntity user = requiredUser(currentActor.requiredUserId());
        if (request.name() != null
                && !Objects.equals(request.name(), user.getName())
                && userMapper.exists(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getName, request.name()))) {
            throw new BusinessException(HttpStatus.CONFLICT, BusinessCode.REGISTER_FAIL, "用户名重复");
        }
        if (request.name() != null) {
            user.setName(request.name());
        }
        if (request.major() != null) {
            user.setMajor(request.major());
        }
        if (request.language() != null) {
            user.setLanguage(request.language());
        }
        if (request.avatar() != null) {
            user.setAvatar(request.avatar());
        }
        try {
            userMapper.updateById(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(HttpStatus.CONFLICT, BusinessCode.REGISTER_FAIL, "用户名重复");
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    BusinessCode.FOREIGN_KEY_CONSTRAINT_VIOLATION,
                    "Invalid favourite language");
        }
        return true;
    }

    /**
     * 变更 IdentityService 对应状态，并维持既有校验、事务及外部副作用边界。
     *
     * @param request 已经过声明式校验的接口请求体
     * @return 当前条件是否成立
     */
    @Transactional
    public boolean changePassword(ChangePasswordRequest request) {
        PasswordPolicy.requireWritable(request.newPassword());
        UserEntity user = requiredUser(currentActor.requiredUserId());
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, BusinessCode.LOGIN_FAIL, "原密码输入错误");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BusinessException(HttpStatus.CONFLICT, BusinessCode.CONFLICT, "新密码与原密码相同");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userMapper.updateById(user);
        sessionRevocationService.revokePrincipal("USER:" + user.getId());
        return true;
    }

    /**
     * 变更 IdentityService 对应状态，并维持既有校验、事务及外部副作用边界。
     *
     * @param request 已经过声明式校验的接口请求体
     * @return 当前条件是否成立
     */
    @Transactional
    public boolean resetPassword(ResetPasswordRequest request) {
        PasswordPolicy.requireWritable(request.newPassword());
        verificationCodeService.consume(
                request.email(), VerificationPurpose.PASSWORD_RESET, request.verificationCode());
        UserEntity user = findByEmail(request.email());
        if (user == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "该用户不存在");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BusinessException(HttpStatus.CONFLICT, BusinessCode.CONFLICT, "新密码与原密码相同");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userMapper.updateById(user);
        sessionRevocationService.revokePrincipal("USER:" + user.getId());
        return true;
    }

    /**
     * 变更 IdentityService 对应状态，并维持既有校验、事务及外部副作用边界。
     *
     * @param request 已经过声明式校验的接口请求体
     * @return 当前条件是否成立
     */
    @Transactional
    public boolean changeAdministratorPassword(AdministratorPasswordRequest request) {
        PasswordPolicy.requireWritable(request.newPassword());
        String administratorId = currentActor.requiredAdministratorId();
        AdministratorEntity administrator = administratorMapper.selectById(administratorId);
        if (administrator == null || !passwordEncoder.matches(request.password(), administrator.getAdminPassword())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, BusinessCode.LOGIN_FAIL, "原密码输入错误");
        }
        if (passwordEncoder.matches(request.newPassword(), administrator.getAdminPassword())) {
            throw new BusinessException(HttpStatus.CONFLICT, BusinessCode.CONFLICT, "新密码与原密码相同");
        }
        administrator.setAdminPassword(passwordEncoder.encode(request.newPassword()));
        administratorMapper.updateById(administrator);
        sessionRevocationService.revokePrincipal("ADMIN:" + administratorId);
        return true;
    }

    /**
     * 查询并返回 IdentityService 中与 findUser 对应的数据，不改变业务状态。
     *
     * @param userId 目标对象的稳定标识
     * @return 存在时包含目标值，否则为空
     */
    @Override
    public Optional<UserSnapshot> findUser(long userId) {
        UserEntity user = userMapper.selectById(userId);
        return Optional.ofNullable(user)
                .map(value -> new UserSnapshot(value.getId(), value.getName(), value.getAvatar()));
    }

    /**
     * 查询并返回 IdentityService 中与 findNotificationContact 对应的数据，不改变业务状态。
     *
     * @param userId 目标对象的稳定标识
     * @return 存在时包含目标值，否则为空
     */
    @Override
    public Optional<NotificationContact> findNotificationContact(long userId) {
        UserEntity user = userMapper.selectById(userId);
        return Optional.ofNullable(user)
                .filter(value -> value.getEmail() != null && !value.getEmail().isBlank())
                .map(value -> new NotificationContact(value.getId(), value.getEmail()));
    }

    /**
     * 查询并返回 IdentityService 中与 findByEmail 对应的数据，不改变业务状态。
     *
     * @param email 调用方提供的 {@code email} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    private UserEntity findByEmail(String email) {
        return userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getEmail, email.trim().toLowerCase(java.util.Locale.ROOT))
                .eq(UserEntity::getState, 0));
    }

    /**
     * AuthenticationAccount 以不可变字段承载身份认证数据并保持既有协议语义。
     *
     * @param id 目标对象的稳定标识
     * @param displayName 调用方提供的 {@code displayName} 值
     * @param encodedPassword 调用方提供的 {@code encodedPassword} 值
     */
    record AuthenticationAccount(String id, String displayName, String encodedPassword) {}

    /**
     * 校验 IdentityService 中与 requiredUser 对应的前置条件，不满足时沿用既有失败语义。
     *
     * @param id 目标对象的稳定标识
     * @return 按当前声明计算、查询或转换得到的结果
     */
    private UserEntity requiredUser(long id) {
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "User does not exist");
        }
        return user;
    }

    /**
     * 执行 IdentityService 中的 toResponse 职责，并保持既有权限、事务与副作用边界。
     *
     * @param user 调用方提供的 {@code user} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    private UserResponse toResponse(UserEntity user) {
        return new UserResponse(
                Long.toString(user.getId()),
                user.getName(),
                user.getEmail(),
                user.getMajor(),
                user.getAvatar(),
                user.getState(),
                user.getTime(),
                user.getLanguage());
    }
}
