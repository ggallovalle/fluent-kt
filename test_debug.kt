import dev.kbroom.fluent.syntax.parser.FluentParser

fun main() {
    val parser = FluentParser()
    val source = """
foo = Foo
    .attr = { "a" ->
                [a] A
               *[b] B
            }
""".trimIndent()
    
    val ast = parser.parse(source)
    println(ast)
}
