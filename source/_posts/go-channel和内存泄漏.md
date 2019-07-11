title: go channel和内存泄漏
author: xdkxlk
tags:
  - go
categories:
  - go
date: 2019-07-10 19:54:00
---
发生内存泄漏的实例
```go
func main() {
    ch := make(chan struct{})

    go func() {
        for {
            ch <- struct{}{}
        }
    }()
    go func() {
        <-ch

        if err := someThing(); err != nil {
            return
        }
    }()

    someOtherThing()
}
```
当接收者异常退出之后，通知生成者，那么就会导致生产者一直阻塞在 channel上面，造成 goroutine leak

解决方法就是 channel不用了，那么就把它关掉，那么怎么合理的关闭 channel呢？

关于关闭 channel注意几个点：
- 一个 channel不能多次关闭，会导致异常
- 向一个已经关闭了的 channel发送数据会导致异常
- 从一个已经关闭了的 channel读取数据不会阻塞，如果以 `data,ok := <- ch`的方式读取，那么 ok为 false，data 为默认的“零”值
- `select...case..` 里面如果有已经关闭的 channel，依然还是有可能走那个已经关闭的 channel（select是伪随机的）


# 解决方法
主要思路是：
- 利用 从一个已经关闭了的 channel读取数据不会阻塞，当关闭的一个 channel的时候就相当于触发了一个广播。于是，可以使用一个单独 `stopChan`，通过 close它来触发通知，让 goroutine知道需要退出
- 但是，这个仅仅只是基本的思路，因为如果有多个消费者同时达到退出条件，那么对于 `stopChan` 的关闭依然会造成重复关闭。解决了思路一个是使用锁，但是更好的方法是使用 context。通过 CancelFunc触发 Done
- 不要在接收端 close channel，这也是 Go不推荐的做法。可以在发送端关闭 channel，或者在 channel的管理者关闭 channel（比如创建这些 channel的 main goroutine）

# 实例一
假设一个场景，有多个生产者向多个消费者发送数字，如果某一个消费者接收到的数字的和大于10，那么所有的生产者和消费者都退出

```go
func muitiSenderReceiverClose() {
    const MaxNum = 3

    wg := sync.WaitGroup{}
    wg.Add(2 * MaxNum)

    intChan := make(chan int, MaxNum)
    ctx, stopFunc := context.WithCancel(context.Background())

    for i := 0; i < MaxNum; i++ {
        go func(i int) {
            defer wg.Done()
            defer fmt.Printf("End. [sender] %d\n", i)
            for {
                select {
                case <-ctx.Done():
                    return
                case intChan <- 1:
                case intChan <- 2:
                case intChan <- 3:
                }
                time.Sleep(time.Duration(rand.Intn(2)+1) * time.Second)
            }
        }(i)
    }

    for i := 0; i < MaxNum; i++ {
        go func(i int) {
            defer wg.Done()
            defer fmt.Printf("End. [receiver] %d\n", i)
            sum := 0
            for {
                select {
                case <-ctx.Done():
                    return
                default:
                    e := <-intChan
                    select {
                    case <-ctx.Done():
                        return
                    default:
                        fmt.Printf("%d Receive %d\n", i, e)
                        sum += e
                        if sum > 10 {
                            // close stopChan通知所有生产者和消费者退出
                            stopFunc()
                            fmt.Printf("%d Got %d\n", i, sum)
                            return
                        }
                    }
                }
            }
        }(i)
    }

    wg.Wait()
    // 注意，在这个例子中，不能在 sender里面关闭 channel
    // 原因：如果 channel已经关闭，select..case..依然可能会选它
    // 关闭intChan
    close(intChan)
    fmt.Printf("End. [cleanup]")
}
```

# 实例二
实现一个 controller控制所有的 worker启动、暂停、终止，并且如果 worker任务完成了，那么就退出

```go
const (
    Stopped = 0
    Paused  = 1
    Running = 2
)

type WorkerCtrl struct {
    statusChan chan int
    ctx        context.Context
    done       context.CancelFunc
}

const WorkerCount = 10

func main() {
    var wg sync.WaitGroup
    wg.Add(WorkerCount + 1)

    workers := make([]WorkerCtrl, WorkerCount)
    for i := range workers {
        workers[i] = WorkerCtrl{statusChan: make(chan int, 1)}
        workers[i].ctx, workers[i].done = context.WithCancel(context.Background())

        go func(i int) {
            worker(i, workers[i])
            wg.Done()
        }(i)
    }

    go func() {
        controller(&workers)
        wg.Done()
    }()

    wg.Wait()
}

func worker(id int, ws WorkerCtrl) {
    state := Paused

    var finishFlag bool
    for {
        select {
        case state = <-ws.statusChan:
            switch state {
            case Stopped:
                fmt.Printf("Worker %d: Stopped\n", id)
                return
            case Running:
                finishFlag = rand.Intn(100) < 30
                fmt.Printf("Worker %d: Running\n", id)
            case Paused:
                fmt.Printf("Worker %d: Paused\n", id)
            }

        default:
            // 主动让出 P
            runtime.Gosched()

            if state == Paused {
                // 如果pause，那么继续循环
                break
            }
            
            if finishFlag {
                // 通知任务完成
                ws.done()
                fmt.Printf("Worker %d: finished\n", id)
                return
            }
        }
    }
}

func controller(workers *[]WorkerCtrl) {
    time.Sleep(time.Second)
    setState(workers, Running)

    time.Sleep(time.Second)
    setState(workers, Paused)

    time.Sleep(time.Second)
    setState(workers, Running)

    time.Sleep(time.Second)
    setState(workers, Stopped)

    for _, w := range *workers {
        // 关闭剩余的 channel
        close(w.statusChan)
    }
}

func setState(workers *[]WorkerCtrl, state int) {
    for i := 0; i < len(*workers); {
        w := (*workers)[i]
        select {
        case <-w.ctx.Done():
            // 此 worker任务完成，close channel并从列表中删除
            close(w.statusChan)
            *workers = append((*workers)[:i], (*workers)[i+1:]...)
        default:
            w.statusChan <- state
            i++
        }
    }
}
```
# 参考
[Is there some elegant way to pause & resume any other goroutine in golang?
](https://stackoverflow.com/questions/16101409/is-there-some-elegant-way-to-pause-resume-any-other-goroutine-in-golang/16102304#16102304)