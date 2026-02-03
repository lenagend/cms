package com.mingchico.cms.core.context.i18n;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface I18nMessageRepository extends JpaRepository<I18nMessage, Long> {
    Optional<I18nMessage> findByCodeAndLocale(String code, String locale);
    boolean existsByCodeAndLocale(String code, String locale);
}