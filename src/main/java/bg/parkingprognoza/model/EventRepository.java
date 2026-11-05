package bg.parkingprognoza.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findBySourceAndExternalId(String source, String externalId);

    List<Event> findByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(LocalDateTime from, LocalDateTime to);

    List<Event> findByStartsAtGreaterThanEqualOrderByStartsAt(LocalDateTime from);

    List<Event> findBySourceAndStartsAtGreaterThanEqual(String source, LocalDateTime from);
}
