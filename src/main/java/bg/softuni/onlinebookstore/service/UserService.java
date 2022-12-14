package bg.softuni.onlinebookstore.service;

import bg.softuni.onlinebookstore.model.dto.book.AddBookToCartDTO;
import bg.softuni.onlinebookstore.model.dto.user.UserOverviewDTO;
import bg.softuni.onlinebookstore.model.dto.user.UserRegistrationDTO;
import bg.softuni.onlinebookstore.model.email.AccountVerificationEmailContext;
import bg.softuni.onlinebookstore.model.entity.BookEntity;
import bg.softuni.onlinebookstore.model.entity.SecureTokenEntity;
import bg.softuni.onlinebookstore.model.entity.UserEntity;
import bg.softuni.onlinebookstore.model.entity.UserRoleEntity;
import bg.softuni.onlinebookstore.model.enums.UserRoleEnum;
import bg.softuni.onlinebookstore.model.error.BookNotFoundException;
import bg.softuni.onlinebookstore.model.error.InvalidTokenException;
import bg.softuni.onlinebookstore.model.mapper.UserMapper;
import bg.softuni.onlinebookstore.repositories.BookRepository;
import bg.softuni.onlinebookstore.repositories.SecureTokenRepository;
import bg.softuni.onlinebookstore.repositories.UserRepository;
import bg.softuni.onlinebookstore.repositories.UserRoleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserDetailsService userDetailsService;
    private final UserRoleRepository userRoleRepository;
    private final BookRepository bookRepository;
    private final SecureTokenService secureTokenService;
    private final SecureTokenRepository secureTokenRepository;
    private final EmailService emailService;

    @Value("${site.base.url}")
    private String baseURL;

    public UserService(UserRepository userRepository, UserMapper userMapper, PasswordEncoder passwordEncoder, UserDetailsService userDetailsService, UserRoleRepository userRoleRepository, BookRepository bookRepository, SecureTokenService secureTokenService, SecureTokenRepository secureTokenRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
        this.userRoleRepository = userRoleRepository;
        this.bookRepository = bookRepository;
        this.secureTokenService = secureTokenService;
        this.secureTokenRepository = secureTokenRepository;
        this.emailService = emailService;
    }

    public void createUserIfNotExists(String email, String name) {
        Optional<UserEntity> userOpt = this.userRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            UserEntity newUser = new UserEntity();
            newUser.setEmail(email);
            newUser.setPassword(null);
            newUser.setFirstName(name.substring(0, name.indexOf(' ')));
            newUser.setLastName(name.substring(name.indexOf(' ') + 1));
            newUser.setAccountVerified(true);
            newUser.addRole(getUserRole());
            userRepository.save(newUser);
        }
    }

    public void register(UserRegistrationDTO userRegistrationDTO) {

        UserEntity newUser = userMapper.userRegistrationDtoToUserEntity(userRegistrationDTO);
        newUser.setPassword(passwordEncoder.encode(userRegistrationDTO.getPassword()));

        newUser.addRole(getUserRole());
        sendRegistrationConfirmationEmail(this.userRepository.save(newUser));
    }

    public void sendRegistrationConfirmationEmail(UserEntity user) {
        SecureTokenEntity secureToken = secureTokenService.createSecureToken();
        secureToken.setUser(user);
        secureTokenRepository.save(secureToken);

        AccountVerificationEmailContext emailContext = new AccountVerificationEmailContext();
        emailContext.init(user);
        emailContext.setToken(secureToken.getToken());
        emailContext.buildVerificationUrl(baseURL, secureToken.getToken());

        try {
            emailService.sendEmail(emailContext);
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }

    public void login(String username) {
        UserDetails userDetails =
                userDetailsService.loadUserByUsername(username);

        Authentication auth =
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        userDetails.getPassword(),
                        userDetails.getAuthorities()
                );

        SecurityContextHolder.
                getContext().
                setAuthentication(auth);
    }

    @PreAuthorize("hasAuthority('ROLE_USER')")
    public boolean addBookToCart(UserDetails userDetails, AddBookToCartDTO bookDTO) {
        Optional<UserEntity> userOpt = userRepository.findByEmail(userDetails.getUsername());
        Optional<BookEntity> bookOpt = bookRepository.findById(bookDTO.getBookId());

        if (bookOpt.isEmpty()) {
            throw new BookNotFoundException(bookDTO.getBookId());
        }

        if (userOpt.isEmpty()) {
            return false;
        }

        UserEntity user = userOpt.get();
        user.addToCart(bookOpt.get(), bookDTO.getQuantity());
        userRepository.save(user);
        return true;
    }


    @Transactional
    public Map<BookEntity, Integer> getUserCart(UserDetails userDetails) {
        return getUser(userDetails).getCart();
    }

    public void removeItemFromCart(Long bookId, UserDetails userDetails) {
        UserEntity user = getUser(userDetails);
        Optional<BookEntity> bookOpt = bookRepository.findById(bookId);
        if (bookOpt.isEmpty()) {
            throw new BookNotFoundException(bookId);
        }

        user.removeFromCart(bookOpt.get());
        userRepository.save(user);
    }

    public void removeAllItemsFromCart(UserDetails userDetails) {
        UserEntity user = getUser(userDetails);
        user.emptyCart();
        userRepository.save(user);
    }

    public UserEntity getUser(UserDetails userDetails) {
        Optional<UserEntity> userOpt = userRepository.findByEmail(userDetails.getUsername());
        if (userOpt.isEmpty() || !userOpt.get().isAccountVerified()) {
            throw new UsernameNotFoundException(userDetails.getUsername());
        }

        return userOpt.get();
    }

    public UserEntity getUser(String email) {
        Optional<UserEntity> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty() || !userOpt.get().isAccountVerified()) {
            throw new UsernameNotFoundException(email);
        }

        return userOpt.get();
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public void addNewAdmin(String username) {
        Optional<UserEntity> userOpt = userRepository.findByEmail(username);
        if (userOpt.isEmpty()) {
            throw new UsernameNotFoundException(username);
        }

        UserEntity user = userOpt.get();
        user.getRoles().remove(getUserRole());
        user.addRole(getAdminRole());
        userRepository.save(user);
    }

    private UserRoleEntity getAdminRole() {
        return userRoleRepository.findByName(UserRoleEnum.ADMIN).get();
    }

    private UserRoleEntity getUserRole() {
        return userRoleRepository.findByName(UserRoleEnum.USER).get();
    }

    public List<UserOverviewDTO> getAllUsersOverview() {
        return userRepository.findAll().stream()
                .filter(u -> !u.getRoles().contains(getAdminRole()))
                .map(u -> new UserOverviewDTO(u.getFullName(), u.getEmail()))
                .collect(Collectors.toList());
    }

    public void verifyUser(String token) {
        Optional<SecureTokenEntity> tokenOpt = secureTokenRepository.findByToken(token);
        if (tokenOpt.isEmpty() || tokenOpt.get().isExpired()) {
            throw new InvalidTokenException("Token is not valid");
        }

        UserEntity user = tokenOpt.get().getUser();
        if (user == null) {
            return;
        }

        user.setAccountVerified(true);
        userRepository.save(user);
        secureTokenRepository.delete(tokenOpt.get());
    }
}
