package uz.karvon.common.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.mock.env.MockEnvironment;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fake providers and dev-only controllers (/api/v1/dev/**, local files) must never be loaded in production.
 * Scans the application's components exactly as Spring does, with the given active profile, so @Profile conditions
 * are applied: a new FakeX or DevX bean without @Profile({"dev", "test"}) makes this test fail.
 */
class DevOnlyBeansTest {

    /** Configurations of the real Taobao, SMS, Telegram, translation, CBU and payment clients (not in dev/test). */
    private static final Set<String> REAL = Set.of("TaobaoConfig", "SmsConfig", "TelegramConfig", "TranslationConfig",
            "CbuConfig", "ProviderCheckoutGateway");

    private static final Set<String> DEV_ONLY = Set.of("DevOtpController", "DevPayController", "LocalFileController");

    @Test
    void noFakeOrDevBeanInProduction() {
        Set<String> prod = components("prod");

        assertThat(prod).as("components scanned with the prod profile").isNotEmpty();
        assertThat(prod).filteredOn(name -> name.startsWith("Fake") || name.startsWith("Dev") || DEV_ONLY.contains(name))
                .as("fake or dev-only beans in prod")
                .isEmpty();
        assertThat(prod).as("the real implementations are there").containsAll(REAL);
    }

    @Test
    void theFakesExistInDev() {
        assertThat(components("dev")).contains("FakeTaobaoProvider", "FakeSmsSender", "FakePaymentProvider",
                "DevOtpController", "DevPayController", "LocalFileController");
        assertThat(components("dev")).as("and the real providers are not").doesNotContainAnyElementsOf(REAL);
    }

    private static Set<String> components(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(true, environment);
        return scanner.findCandidateComponents("uz.karvon").stream()
                .map(BeanDefinition::getBeanClassName)
                .map(name -> name.substring(name.lastIndexOf('.') + 1))
                .collect(Collectors.toSet());
    }
}
