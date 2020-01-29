package tat.mukhutdinov.calculator

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private val calculator = Calculator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        calculate.setOnClickListener {
            val num1 = first.text.toString().toIntOrNull()
            val num2 = second.text.toString().toIntOrNull()

            if (num1 != null && num2 != null) {
                result.text = "${calculator.subtract(num1, num2)}"
            } else {
                result.text = ""
            }

        }
    }
}
