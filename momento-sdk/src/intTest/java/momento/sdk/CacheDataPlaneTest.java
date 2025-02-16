package momento.sdk;

import static momento.sdk.TestUtils.randomString;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import momento.sdk.auth.CredentialProvider;
import momento.sdk.auth.StringCredentialProvider;
import momento.sdk.config.Configurations;
import momento.sdk.exceptions.AuthenticationException;
import momento.sdk.exceptions.NotFoundException;
import momento.sdk.exceptions.TimeoutException;
import momento.sdk.responses.cache.DeleteResponse;
import momento.sdk.responses.cache.GetResponse;
import momento.sdk.responses.cache.SetResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests with Async APIs. */
final class CacheDataPlaneTest extends BaseTestClass {

  private static final Duration DEFAULT_ITEM_TTL_SECONDS = Duration.ofSeconds(60);

  private final String cacheName = System.getenv("TEST_CACHE_NAME");

  private CacheClient client;

  @BeforeEach
  void setup() {
    client =
        CacheClient.builder(
                credentialProvider, Configurations.Laptop.latest(), DEFAULT_ITEM_TTL_SECONDS)
            .build();
  }

  @AfterEach
  void teardown() {
    client.close();
  }

  @Test
  void getReturnsHitAfterSet() {
    final String key = randomString("key");
    final String value = randomString("value");

    // Successful Set
    final SetResponse setResponse = client.set(cacheName, key, value).join();
    assertThat(setResponse).isInstanceOf(SetResponse.Success.class);
    assertThat(((SetResponse.Success) setResponse).valueString()).isEqualTo(value);
    assertThat(((SetResponse.Success) setResponse).valueByteArray()).isEqualTo(value.getBytes());

    // Successful Get with Hit
    final GetResponse getResponse = client.get(cacheName, key).join();
    assertThat(getResponse).isInstanceOf(GetResponse.Hit.class);
    assertThat(((GetResponse.Hit) getResponse).valueString()).isEqualTo(value);
  }

  @Test
  void cacheMissSuccess() {
    // Get key that was not set
    final GetResponse response = client.get(cacheName, randomString("key")).join();
    assertThat(response).isInstanceOf(GetResponse.Miss.class);
  }

  @Test
  void itemDroppedAfterTtlExpires() throws Exception {
    final String key = randomString("key");

    // Set Key sync
    client.set(cacheName, key, "", Duration.ofSeconds(1)).join();

    Thread.sleep(2000);

    // Get Key that was just set
    final GetResponse rsp = client.get(cacheName, key).join();
    assertThat(rsp).isInstanceOf(GetResponse.Miss.class);
  }

  @Test
  void badTokenReturnsAuthenticationError() {
    final String badToken =
        "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJpbnRlZ3JhdGlvbiIsImNwIjoiY29udHJvbC5jZWxsLWFscGhhLWRldi5"
            + "wcmVwcm9kLmEubW9tZW50b2hxLmNvbSIsImMiOiJjYWNoZS5jZWxsLWFscGhhLWRldi5wcmVwcm9kLmEub"
            + "W9tZW50b2hxLmNvbSJ9.gdghdjjfjyehhdkkkskskmmls76573jnajhjjjhjdhnndy";
    final CredentialProvider badTokenProvider = new StringCredentialProvider(badToken);
    try (final CacheClient client =
        CacheClient.builder(
                badTokenProvider, Configurations.Laptop.latest(), DEFAULT_ITEM_TTL_SECONDS)
            .build()) {

      final GetResponse response = client.get(cacheName, "").join();
      assertThat(response).isInstanceOf(GetResponse.Error.class);
      assertThat(((GetResponse.Error) response)).hasCauseInstanceOf(AuthenticationException.class);
    }
  }

  @Test
  public void nonExistentCacheNameReturnsErrorOnGetOrSet() {
    final String cacheName = randomString("name");

    final GetResponse getResponse = client.get(cacheName, "").join();
    assertThat(getResponse).isInstanceOf(GetResponse.Error.class);
    assertThat(((GetResponse.Error) getResponse)).hasCauseInstanceOf(NotFoundException.class);

    final SetResponse setResponse = client.set(cacheName, "", "", Duration.ofSeconds(10)).join();
    assertThat(setResponse).isInstanceOf(SetResponse.Error.class);
    assertThat(((SetResponse.Error) setResponse)).hasCauseInstanceOf(NotFoundException.class);
  }

  @Test
  public void getWithShortTimeoutReturnsError() {
    try (final CacheClient client =
        CacheClient.builder(
                credentialProvider, Configurations.Laptop.latest(), DEFAULT_ITEM_TTL_SECONDS)
            .setDeadline(Duration.ofMillis(1))
            .build()) {

      final GetResponse response = client.get("cache", "key").join();
      assertThat(response).isInstanceOf(GetResponse.Error.class);
      assertThat(((GetResponse.Error) response)).hasCauseInstanceOf(TimeoutException.class);
    }
  }

  @Test
  public void allowEmptyKeyValues() throws Exception {
    final String emptyKey = "";
    final String emptyValue = "";
    client.set(cacheName, emptyKey, emptyValue).get();
    final GetResponse response = client.get(cacheName, emptyKey).get();
    assertThat(response).isInstanceOf(GetResponse.Hit.class);
    assertThat(((GetResponse.Hit) response).valueString()).isEqualTo(emptyValue);
  }

  @Test
  public void deleteHappyPath() throws Exception {
    final String key = "key";
    final String value = "value";

    client.set(cacheName, key, value).get();
    final GetResponse getResponse = client.get(cacheName, key).get();
    assertThat(getResponse).isInstanceOf(GetResponse.Hit.class);
    assertThat(((GetResponse.Hit) getResponse).valueString()).isEqualTo(value);

    final DeleteResponse deleteResponse = client.delete(cacheName, key).get();
    assertThat(deleteResponse).isInstanceOf(DeleteResponse.Success.class);

    final GetResponse getAfterDeleteResponse = client.get(cacheName, key).get();
    assertThat(getAfterDeleteResponse).isInstanceOf(GetResponse.Miss.class);
  }

  @Test
  public void setWithShortTimeoutReturnsError() {
    try (CacheClient client =
        CacheClient.builder(
                credentialProvider, Configurations.Laptop.latest(), DEFAULT_ITEM_TTL_SECONDS)
            .setDeadline(Duration.ofMillis(1))
            .build()) {

      final SetResponse response = client.set("cache", "key", "value").join();
      assertThat(response).isInstanceOf(SetResponse.Error.class);
      assertThat(((SetResponse.Error) response)).hasCauseInstanceOf(TimeoutException.class);
    }
  }
}
