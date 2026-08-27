package com.cc4c.identity.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cc4c.identity.IdentityDtos.ChangePasswordRequest;
import com.cc4c.identity.IdentityDtos.RegisterRequest;
import com.cc4c.identity.IdentityDtos.ResetPasswordRequest;
import com.cc4c.identity.IdentityDtos.UserResponse;
import com.cc4c.identity.IdentityDtos.UserUpdateRequest;
import com.cc4c.identity.api.IdentityLookup;
import com.cc4c.identity.api.UserSnapshot;
import com.cc4c.shared.BusinessCode;
import com.cc4c.shared.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Objects;
import java.util.Optional;

@Service
public class IdentityService implements IdentityLookup {
    private final UserMapper userMapper;
    private final AdministratorMapper administratorMapper;

    IdentityService(UserMapper userMapper, AdministratorMapper administratorMapper) {
        this.userMapper = userMapper;
        this.administratorMapper = administratorMapper;
    }

    @Transactional
    public boolean register(RegisterRequest request) {
        if (userMapper.exists(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getName, request.name()))) {
            throw new BusinessException(HttpStatus.CONFLICT, BusinessCode.REGISTER_FAIL, "用户名重复");
        }
        if (userMapper.exists(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getEmail, request.email()))) {
            throw new BusinessException(HttpStatus.CONFLICT, BusinessCode.REGISTER_FAIL, "该邮箱已经被注册");
        }

        UserEntity user = new UserEntity();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPassword(request.password());
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

    public boolean login(String email, String password) {
        UserEntity user = findByEmail(email);
        if (user == null || !Objects.equals(user.getPassword(), password)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, BusinessCode.LOGIN_FAIL, "邮箱或密码错误");
        }
        return true;
    }

    public boolean administratorLogin(String id, String password) {
        AdministratorEntity administrator = administratorMapper.selectById(id);
        if (administrator == null || !Objects.equals(administrator.getAdminPassword(), password)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, BusinessCode.LOGIN_FAIL, "管理员账号或密码错误");
        }
        return true;
    }

    public boolean administratorExists(String id) {
        return id != null && !id.isBlank() && administratorMapper.selectById(id) != null;
    }

    public boolean userExistsByEmail(String email) {
        return email != null && !email.isBlank() && findByEmail(email) != null;
    }

    public UserResponse userByEmail(String email) {
        UserEntity user = findByEmail(email);
        if (user == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, BusinessCode.UNAUTHORIZED, "请先登录");
        }
        return toResponse(user);
    }

    @Transactional
    public boolean update(UserUpdateRequest request) {
        UserEntity user = requiredUser(request.id());
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
        UserEntity user = requiredUser(request.id());
        if (!Objects.equals(user.getPassword(), request.password())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, BusinessCode.LOGIN_FAIL, "原密码输入错误");
        }
        if (Objects.equals(user.getPassword(), request.newPassword())) {
            throw new BusinessException(HttpStatus.CONFLICT, BusinessCode.CONFLICT, "新密码与原密码相同");
        }
        user.setPassword(request.newPassword());
        userMapper.updateById(user);
        return true;
    }

    @Transactional
    public boolean resetPassword(ResetPasswordRequest request) {
        UserEntity user = findByEmail(request.email());
        if (user == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "该用户不存在");
        }
        if (Objects.equals(user.getPassword(), request.newPassword())) {
            throw new BusinessException(HttpStatus.CONFLICT, BusinessCode.CONFLICT, "新密码与原密码相同");
        }
        user.setPassword(request.newPassword());
        userMapper.updateById(user);
        return true;
    }

    @Override
    public Optional<UserSnapshot> findUser(long userId) {
        UserEntity user = userMapper.selectById(userId);
        return Optional.ofNullable(user)
                .map(value -> new UserSnapshot(value.getId(), value.getName(), value.getAvatar()));
    }

    private UserEntity findByEmail(String email) {
        return userMapper.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getEmail, email));
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
