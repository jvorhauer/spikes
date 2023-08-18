package spikes.validate

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import spikes.validate.Validation.validate

class RuleTests extends AnyFlatSpec with Matchers {

  "URL" should "validate" in {
    validate(urlRule("https://www.miruvor.nl/users/123?page=1"), "https://www.miruvor.nl/users/123?page=1", "url") should be(None)
    validate(urlRule("bla bla"), "bla bla", "url-no-protocl") should not be None
    validate(urlRule("http:/flensje"), "http:/flensje", "url-only-protocol") should not be None
    validate(urlRule("http://www.nu.nl"), "ftp://dinges:", "url-without-port") should not be None
  }

  "Slug" should "validate" in {
    validate(
      slugRule("20230708-title-of-my-blog"),
      "20230708-title-of-my-blog",
      "slug"
    ) should be (None)
  }

  "Optional Color" should "validate" in {
    validate(
      colorRule(Some("000000")),
      Some("000000"),
      "color"
    ) should be (None)
    validate(colorRule(None), None, "color") should be (None)

    validate(colorRule(Some("09ACD")), Some("he, wat?"), "color") should not be empty
    validate(colorRule(Some("AABBCCD")), Some("he, wat?"), "color") should not be empty
    validate(colorRule(Some("09ABFG")), Some("he, wat?"), "color") should not be empty
  }
}
