package com.cc4c.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cc4c.common.BusinessCode;
import com.cc4c.common.BusinessException;
import com.cc4c.dto.IdentityDtos.AdministratorPasswordRequest;
import com.cc4c.dto.IdentityDtos.ChangePasswordRequest;
import com.cc4c.dto.IdentityDtos.RegisterRequest;
import com.cc4c.dto.IdentityDtos.ResetPasswordRequest;
import com.cc4c.dto.IdentityDtos.UserResponse;
import com.cc4c.dto.IdentityDtos.UserUpdateRequest;
import com.cc4c.dto.IdentityDtos.VerificationPurpose;
import com.cc4c.dto.NotificationContact;
import com.cc4c.dto.UserSnapshot;
import com.cc4c.entity.AdministratorEntity;
import com.cc4c.entity.UserEntity;
import com.cc4c.mapper.AdministratorMapper;
import com.cc4c.mapper.UserMapper;
import com.cc4c.security.AccountRole;
import com.cc4c.security.CurrentActor;
import com.cc4c.security.PasswordPolicy;
import com.cc4c.security.SessionRevocationService;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 维护用户注册、资料和密码，并提供认证账户、公开快照及内部通知联系方式查询。 */
@Service
public class IdentityService implements IdentityLookup, IdentityNotificationLookup {
    private final UserMapper userMapper;
    private final AdministratorMapper administratorMapper;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeService verificationCodeService;
    private final CurrentActor currentActor;
    private final SessionRevocationService sessionRevocationService;

    /**
     * 接入用户与管理员 Mapper、密码编码、验证码、当前身份及会话撤销服务。
     *
     * @param userMapper 用户数据访问 Mapper
     * @param administratorMapper 管理员数据访问 Mapper
     * @param passwordEncoder 业务密码编码与比对器
     * @param verificationCodeService 按邮箱及用途消费验证码的服务
     * @param currentActor 当前业务身份读取接口
     * @param sessionRevocationService 按身份名撤销全部会话的服务
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
     * 校验密码并消费注册验证码后检查用户名和邮箱唯一性，再插入编码后的用户资料；Redis 验证码消费不随数据库事务回滚。
     *
     * @param request 包含新用户资料及注册验证码的请求
     * @return 用户插入成功后为 true
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
     * 用户按规范化邮箱和启用状态查询，管理员按编号查询，仅返回认证所需字段。
     *
     * @param role 待认证的 USER 或 ADMIN 角色
     * @param identifier 用户邮箱或管理员编号
     * @return 存在时返回包含编码密码的内部认证账户
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
     * 要求当前身份为 USER 并加载用户资料。
     *
     * @return 不含密码的当前用户响应
     */
    public UserResponse currentUser() {
        return toResponse(requiredUser(currentActor.requiredUserId()));
    }

    /**
     * 在事务内更新当前用户的非空资料字段，检查昵称重复并转换唯一键及语言外键错误。
     *
     * @param request 当前用户的可选资料更新字段
     * @return 资料更新流程完成后为 true
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
     * 验证当前 USER 的旧密码，拒绝重复新密码，更新编码密码后请求撤销该用户的全部会话。
     *
     * @param request 当前密码与新密码请求
     * @return 密码更新和会话撤销调用完成后为 true
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
     * 消费重置验证码后查找启用用户，更新新密码并请求撤销全部用户会话；验证码消费不随数据库回滚恢复。
     *
     * @param request 邮箱、重置验证码及新密码请求
     * @return 密码更新和会话撤销调用完成后为 true
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
     * 验证当前 ADMIN 的旧密码，拒绝相同新密码，保存新编码密码并请求撤销该管理员的全部会话。
     *
     * @param request 管理员当前密码与新密码请求
     * @return 密码更新和会话撤销调用完成后为 true
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
     * 按 ID 读取用户，并只投影 ID、昵称和头像。
     *
     * @param userId 用户 ID
     * @return 用户资料快照；不存在时为空 Optional
     */
    @Override
    public Optional<UserSnapshot> findUser(long userId) {
        UserEntity user = userMapper.selectById(userId);
        return Optional.ofNullable(user)
                .map(value -> new UserSnapshot(value.getId(), value.getName(), value.getAvatar()));
    }

    /**
     * 按 ID 读取用户，仅在邮箱非空时生成通知联系方式。
     *
     * @param userId 用户 ID
     * @return 可用通知联系方式，否则为空 Optional
     */
    @Override
    public Optional<NotificationContact> findNotificationContact(long userId) {
        UserEntity user = userMapper.selectById(userId);
        return Optional.ofNullable(user)
                .filter(value -> value.getEmail() != null && !value.getEmail().isBlank())
                .map(value -> new NotificationContact(value.getId(), value.getEmail()));
    }

    /**
     * 将邮箱去空白并小写化，查询状态为 0 的用户。
     *
     * @param email 账户或验证码收件邮箱
     * @return 匹配的启用用户，不存在时为空
     */
    private UserEntity findByEmail(String email) {
        return userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getEmail, email.trim().toLowerCase(java.util.Locale.ROOT))
                .eq(UserEntity::getState, 0));
    }

    /**
     * 供认证提供器使用的账户字段，包含编码密码，不用于接口响应。
     *
     * @param id 用户或管理员身份 ID
     * @param displayName 认证成功后使用的身份展示名
     * @param encodedPassword 仅供认证比对的编码密码，不得记录
     */
    public record AuthenticationAccount(String id, String displayName, String encodedPassword) {}

    /**
     * 按用户 ID 加载记录，不存在时抛出 404 业务异常。
     *
     * @param id 用户或管理员身份 ID
     * @return 存在的用户实体
     */
    private UserEntity requiredUser(long id) {
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "User does not exist");
        }
        return user;
    }

    /**
     * 将用户实体投影为无密码响应，并将长整数 ID 转为字符串。
     *
     * @param user 待转换的用户实体
     * @return 用户资料响应
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
