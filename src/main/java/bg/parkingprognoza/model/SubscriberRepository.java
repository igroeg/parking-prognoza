package bg.parkingprognoza.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriberRepository extends JpaRepository<Subscriber, Long> {

    Optional<Subscriber> findByEmailIgnoreCase(String email);

    Optional<Subscriber> findByToken(String token);

    List<Subscriber> findByConfirmedTrue();
}
