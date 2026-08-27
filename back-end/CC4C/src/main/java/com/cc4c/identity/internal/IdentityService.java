package com.cc4c.identity.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cc4c.identity.IdentityDtos.ChangePasswordRequest;
import com.cc4c.identity.IdentityDtos.RegisterRequest;
import com.cc4c.identity.IdentityDtos.ResetPasswordRequest;
import com.cc4c.identity.IdentityDtos.UserResponse;
import com.cc4c.identity.IdentityDtos.UserUpdateRequest;
import com.cc4c.identity.IdentityDtos.AdministratorPasswordRequest;
import com.cc4c.identity.IdentityDtos.VerificationPurpose;
import com.cc4c.identity.api.AccountRole;
import com.cc4c.identity.api.CurrentActor;
import com.cc4c.identity.api.IdentityLookup;
import com.cc4c.identity.api.UserSnapshot;
import com.cc4c.shared.BusinessCode;
import com.cc4c.shared.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Objects;
import java.util.Optional;

@Service
public class IdentityService implements IdentityLookup {
    private final UserMapper userMapper;
    private final AdministratorMapper administratorMapper;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeService verificationCodeService;
    private final CurrentActor currentActor;
    private final SessionRevocationService sessionRevocationService;

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

    @Transactional
    public boolean register(RegisterRequest request) {
        PasswordPolicy.requireWritable(request.password());
        verificationCodeService.consume(
                request.email(), VerificationPurpose.REGISTER, request.verificationCode());
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
            throw new BusinessException(
                    HttpStatus.CONFLICT, BusinessCode.REGISTER_FAIL, "用户名或邮箱已存在");
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    BusinessCode.FOREIGN_KEY_CONSTRAINT_VIOLATION,
                    "Invalid favourite language");
        }
        return true;
    }

    public Optional<AuthenticationAccount> authenticationAccount(
            AccountRole role, String identifier) {
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
                .map(value -> new AuthenticationAccount(
                        value.getAdminId(), value.getAdminId(), value.getAdminPassword()));
    }

    public UserResponse currentUser() {
        return toResponse(requiredUser(currentActor.requiredUserId()));
    }

    @Transactional
    public boolean update(UserUpdateRequest request) {
        UserEntity user = requiredUser(currentActor.requiredUserId());
        if (request.name() != null && !Objects.equals(request.name(), user.getName())
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

    @Transactional
    public boolean changeAdministratorPassword(AdministratorPasswordRequest request) {
        PasswordPolicy.requireWritable(request.newPassword());
        String administratorId = currentActor.requiredAdministratorId();
        AdministratorEntity administrator = administratorMapper.selectById(administratorId);
        if (administrator == null || !passwordEncoder.matches(
                request.password(), administrator.getAdminPassword())) {
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

    @Override
    public Optional<UserSnapshot> findUser(long userId) {
        UserEntity user = userMapper.selectById(userId);
        return Optional.ofNullable(user)
                .map(value -> new UserSnapshot(value.getId(), value.getName(), value.getAvatar()));
    }

    private UserEntity findByEmail(String email) {
        return userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getEmail, email.trim().toLowerCase(java.util.Locale.ROOT))
                .eq(UserEntity::getState, 0));
    }

    record AuthenticationAccount(String id, String displayName, String encodedPassword) {
    }

    private UserEntity requiredUser(long id) {
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "User does not exist");
        }
        return user;
    }

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
