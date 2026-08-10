package com.selflearning.authservice.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.selflearning.authservice.auth.domain.AuthUser;
import com.selflearning.authservice.auth.mapper.AuthUserMapper;
import com.selflearning.authservice.auth.request.UserCreateRequest;
import com.selflearning.authservice.auth.request.UserStatusRequest;
import com.selflearning.authservice.auth.request.UserUpdateRequest;
import com.selflearning.authservice.auth.response.UserResponse;
import com.selflearning.authservice.common.web.BadRequestException;
import com.selflearning.authservice.common.web.NotFoundException;
import com.selflearning.authservice.common.web.PageResponse;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final int DEFAULT_STATUS_ENABLED = 1;
    private static final long DEFAULT_PAGE = 1L;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;

    private final AuthUserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final PermissionContextCacheService permissionContextCacheService;

    public UserService(
            AuthUserMapper userMapper,
            BCryptPasswordEncoder passwordEncoder,
            PermissionContextCacheService permissionContextCacheService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.permissionContextCacheService = permissionContextCacheService;
    }

    /**
     * 分页查询用户列表。
     *
     * @param keyword 用户名、昵称、邮箱或手机号关键字
     * @param status 用户状态，1 表示启用，0 表示停用
     * @param page 当前页码，从 1 开始
     * @param pageSize 每页条数，最大 100
     * @return 用户分页结果
     */
    public PageResponse<UserResponse> pageUsers(String keyword, Integer status, Long page, Long pageSize) {
        long normalizedPage = normalizePage(page);
        long normalizedPageSize = normalizePageSize(pageSize);
        validateStatus(status);

        Page<AuthUser> resultPage = userMapper.selectPage(
                Page.of(normalizedPage, normalizedPageSize),
                baseQuery(keyword, status)
                        .orderByDesc(AuthUser::getCreatedAt)
                        .orderByDesc(AuthUser::getId));

        return new PageResponse<>(
                resultPage.getRecords().stream()
                        .map(UserResponse::from)
                        .toList(),
                resultPage.getTotal(),
                resultPage.getCurrent(),
                resultPage.getSize());
    }

    /**
     * 根据 ID 查询用户详情。
     *
     * @param id 用户ID
     * @return 用户详情
     */
    public UserResponse getUser(Long id) {
        return UserResponse.from(requireExistingUser(id));
    }

    /**
     * 创建用户。
     *
     * @param request 创建请求
     * @return 新建用户详情
     */
    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        AuthUser user = new AuthUser();
        user.setUsername(request.username().trim());
        user.setPasswordHash(encodePassword(request.password()));
        user.setNickname(trimToNull(request.nickname()));
        user.setEmail(trimToNull(request.email()));
        user.setPhone(trimToNull(request.phone()));
        user.setStatus(request.status() == null ? DEFAULT_STATUS_ENABLED : request.status());
        user.setDeleted(false);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw new BadRequestException("Username already exists");
        }

        return UserResponse.from(userMapper.selectById(user.getId()));
    }

    /**
     * 更新用户基础信息。
     *
     * @param id 用户ID
     * @param request 更新请求
     * @return 更新后的用户详情
     */
    @Transactional
    public UserResponse updateUser(Long id, UserUpdateRequest request) {
        AuthUser existing = requireExistingUser(id);
        Integer previousStatus = existing.getStatus();
        existing.setNickname(trimToNull(request.nickname()));
        existing.setEmail(trimToNull(request.email()));
        existing.setPhone(trimToNull(request.phone()));
        if (request.password() != null) {
            existing.setPasswordHash(encodePassword(request.password()));
        }
        existing.setStatus(request.status() == null ? existing.getStatus() : request.status());
        userMapper.updateById(existing);
        evictUserContextIfStatusChanged(existing.getId(), previousStatus, existing.getStatus());
        return UserResponse.from(requireExistingUser(id));
    }

    /**
     * 更新用户启停状态。
     *
     * @param id 用户ID
     * @param request 状态请求
     * @return 更新后的用户详情
     */
    @Transactional
    public UserResponse updateStatus(Long id, UserStatusRequest request) {
        AuthUser existing = requireExistingUser(id);
        Integer previousStatus = existing.getStatus();
        existing.setStatus(request.status());
        userMapper.updateById(existing);
        evictUserContextIfStatusChanged(existing.getId(), previousStatus, existing.getStatus());
        return UserResponse.from(requireExistingUser(id));
    }

    /**
     * 逻辑删除用户。
     *
     * @param id 用户ID
     */
    @Transactional
    public void deleteUser(Long id) {
        AuthUser existing = requireExistingUser(id);
        existing.setDeleted(true);
        userMapper.updateById(existing);
        permissionContextCacheService.evictUserAllApplications(existing.getId());
    }

    private void evictUserContextIfStatusChanged(Long userId, Integer previousStatus, Integer currentStatus) {
        if (!Objects.equals(currentStatus, previousStatus)) {
            permissionContextCacheService.evictUserAllApplications(userId);
        }
    }

    private String encodePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new BadRequestException("Password is required");
        }
        return passwordEncoder.encode(password);
    }

    private LambdaQueryWrapper<AuthUser> baseQuery(String keyword, Integer status) {
        LambdaQueryWrapper<AuthUser> wrapper = new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getDeleted, false);
        if (status != null) {
            wrapper.eq(AuthUser::getStatus, status);
        }
        String normalizedKeyword = trimToNull(keyword);
        if (normalizedKeyword != null) {
            wrapper.and(query -> query
                    .like(AuthUser::getUsername, normalizedKeyword)
                    .or()
                    .like(AuthUser::getNickname, normalizedKeyword)
                    .or()
                    .like(AuthUser::getEmail, normalizedKeyword)
                    .or()
                    .like(AuthUser::getPhone, normalizedKeyword));
        }
        return wrapper;
    }

    private AuthUser requireExistingUser(Long id) {
        if (id == null || id <= 0) {
            throw new BadRequestException("Invalid user id");
        }
        AuthUser user = userMapper.selectOne(new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getId, id)
                .eq(AuthUser::getDeleted, false)
                .last("LIMIT 1"));
        if (user == null) {
            throw new NotFoundException("User not found");
        }
        return user;
    }

    private long normalizePage(Long page) {
        if (page == null) {
            return DEFAULT_PAGE;
        }
        if (page < 1) {
            throw new BadRequestException("Page must be greater than 0");
        }
        return page;
    }

    private long normalizePageSize(Long pageSize) {
        if (pageSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BadRequestException("Page size must be between 1 and 100");
        }
        return pageSize;
    }

    private void validateStatus(Integer status) {
        if (status != null && status != 0 && status != 1) {
            throw new BadRequestException("Status must be 0 or 1");
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
