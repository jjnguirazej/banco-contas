package mz.contas.auth;

import java.util.Optional;
import mz.contas.domain.AppUser;
import mz.contas.repository.AppUserRepository;
import mz.contas.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService attempts;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       LoginAttemptService attempts) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.attempts = attempts;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String username = request.utilizador().trim();

        // 1. Utilizador bloqueado? Recusa já, sem sequer verificar a palavra-passe
        //    (senão o atacante continuaria a poder testar palavras-passe).
        attempts.assertNotBlocked(username);

        // 2. A mesma mensagem para utilizador inexistente e palavra-passe errada:
        //    não revelamos quais utilizadores existem.
        Optional<AppUser> user = users.findByUsername(username)
                .filter(AppUser::isEnabled)
                .filter(u -> passwordEncoder.matches(request.palavraPasse(), u.getPasswordHash()));

        if (user.isEmpty()) {
            attempts.recordFailure(username);
            throw new BadCredentialsException("Credenciais inválidas");
        }

        attempts.reset(username);
        AppUser u = user.get();
        String name = u.getCustomer() != null ? u.getCustomer().getFullName() : u.getUsername();
        return new LoginResponse(jwtService.generate(u), "Bearer", jwtService.ttlSeconds(), u.getRole(), name);
    }
}

