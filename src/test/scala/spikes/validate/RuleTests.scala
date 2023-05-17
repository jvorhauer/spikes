package spikes.validate

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RuleTests extends AnyFlatSpec with Matchers {

  "URL" should "validate" in {
    Validation.validate(urlRule("https://www.miruvor.nl/users/123?page=1"), "https://www.miruvor.nl/users/123?page=1", "url") should be(None)
    Validation.validate(urlRule("bla bla"), "bla bla", "url-no-protocl") should not be None
    Validation.validate(urlRule("http:/flensje"), "http:/flensje", "url-only-protocol") should not be None
    Validation.validate(urlRule("http://www.nu.nl"), "ftp://dinges:", "url-without-port") should not be None
  }
}
