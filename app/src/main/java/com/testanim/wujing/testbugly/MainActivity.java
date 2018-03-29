package com.testanim.wujing.testbugly;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    Button btn;
    Button newbtn;
    List<Integer> list ;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btn = findViewById(R.id.btn);
        newbtn = findViewById(R.id.newbtn);
        btn.setOnClickListener(this);
        newbtn.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn:
//                Toast.makeText(this,list.size() +"错了吧",Toast.LENGTH_SHORT).show();
                Toast.makeText(this, "改好了", Toast.LENGTH_SHORT).show();
                break;
            case R.id.newbtn:
//                Toast.makeText(this,"没效果吧",Toast.LENGTH_SHORT).show();
                startActivity(new Intent(MainActivity.this, Main2Activity.class));
                break;

        }
    }
}
