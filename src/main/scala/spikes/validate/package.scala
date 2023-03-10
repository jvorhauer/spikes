package spikes

import scala.util.matching.Regex

package object validate {
  val rname: Regex = "^[\\p{L}\\p{Space}'-]+$".r
  val remail: Regex = "^([\\w-]+(?:\\.[\\w-]+)*)@\\w[\\w.-]+\\.[a-zA-Z]+$".r
  val rpassw: Regex = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#&()–[{}]:;',?/*~$^+=<>]).{8,42}$".r
}
