package lab.simucity.app.identity;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Usuario autenticado. Los controladores obtienen de aquí el ID del dueño: nunca del cuerpo ni de la URL.
 * Se guarda en la sesión, así que no debe contener datos sensibles más allá del hash ya necesario para Spring.
 */
public record AppUserPrincipal(UUID id, String email, String passwordHash, boolean active) implements UserDetails {

    static AppUserPrincipal from(UserAccount user) {
        return new AppUserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash(),
                user.getStatus() == UserStatus.ACTIVE);
    }

    /** Copia sin el hash: es la que se guarda en la sesión tras autenticar. */
    AppUserPrincipal withoutPassword() {
        return new AppUserPrincipal(id, email, null, active);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    /** El hash no debe aparecer en logs. */
    @Override
    public String toString() {
        return "AppUserPrincipal[id=" + id + ", email=" + email + "]";
    }
}
