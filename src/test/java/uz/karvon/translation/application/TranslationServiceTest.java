package uz.karvon.translation.application;

import org.junit.jupiter.api.Test;
import uz.karvon.translation.domain.Lang;
import uz.karvon.translation.infrastructure.TranslationCacheStore;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TranslationServiceTest {

    private final Translator translator = mock(Translator.class);
    private final TranslationCacheStore cache = mock(TranslationCacheStore.class);
    private final TranslationService service = new TranslationService(translator, cache);

    @Test
    void onlyMissingStringsGoToTheProviderOnceEach() {
        when(cache.find(anyCollection(), eq(Lang.ZH), eq(Lang.UZ)))
                .thenReturn(Map.of(TranslationService.hash("黑色"), "Qora"));
        when(translator.translateBatch(List.of("白色", "运动鞋"), Lang.ZH, Lang.UZ)).thenReturn(List.of("Oq", "Krossovka"));

        List<String> result = service.translateBatch(List.of("黑色", "白色", " 运动鞋 ", "白色"), Lang.ZH, Lang.UZ);

        assertThat(result).containsExactly("Qora", "Oq", "Krossovka", "Oq");
        verify(cache).saveAll(Map.of(TranslationService.hash("白色"), "Oq", TranslationService.hash("运动鞋"), "Krossovka"),
                Lang.ZH, Lang.UZ);
    }

    @Test
    void fullCacheHitDoesNotCallTheProvider() {
        when(cache.find(anyCollection(), eq(Lang.UZ), eq(Lang.ZH)))
                .thenReturn(Map.of(TranslationService.hash("krossovka"), "运动鞋"));

        assertThat(service.translate("krossovka", Lang.UZ, Lang.ZH)).isEqualTo("运动鞋");
        verifyNoInteractions(translator);
        verify(cache, never()).saveAll(any(), any(), any());
    }

    @Test
    void sameLanguageAndBlankTextsAreReturnedAsTheyAre() {
        assertThat(service.translateBatch(List.of("Salom"), Lang.UZ, Lang.UZ)).containsExactly("Salom");
        when(cache.find(anyCollection(), any(), any())).thenReturn(Map.of());

        assertThat(service.translateBatch(Arrays.asList("", null, "  "), Lang.ZH, Lang.RU))
                .containsExactly("", null, "  ");
        verify(translator, never()).translateBatch(anyList(), any(), any());
    }

    @Test
    void hashIsSha256Hex() {
        assertThat(TranslationService.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
