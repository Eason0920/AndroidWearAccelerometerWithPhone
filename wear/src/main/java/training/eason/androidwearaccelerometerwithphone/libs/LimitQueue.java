package training.eason.androidwearaccelerometerwithphone.libs;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

/**
 * 實現一個固定長度的集合
 *
 * @param <E>
 */
public class LimitQueue<E> implements Queue<E> {

    /**
     * 集合長度，建構的时候指定
     */
    private int limit;

    Queue<E> queue = new LinkedList<>();

    public LimitQueue(int limit){
        this.limit = limit;
    }

    /**
     * 加入
     */
    @Override
    public boolean offer(E e){
        if(queue.size() >= limit){

            //如果超出長度,加入前,先把最前面移除
            queue.poll();
        }
        return queue.offer(e);
    }

    /**
     * 移除
     */
    @Override
    public E poll() {
        return queue.poll();
    }

    /**
     * 取得集合
     */
    public Queue<E> getQueue(){
        return queue;
    }

    /**
     * 取得集合限制大小
     */
    public int getLimit(){
        return limit;
    }

    @Override
    public boolean add(E e) {
        return queue.add(e);
    }

    @Override
    public E element() {
        return queue.element();
    }

    @Override
    public E peek() {
        return queue.peek();
    }

    @Override
    public boolean isEmpty() {
        return queue.size() == 0;
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public E remove() {
        return queue.remove();
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        return queue.addAll(c);
    }

    @Override
    public void clear() {
        queue.clear();
    }

    @Override
    public boolean contains(Object o) {
        return queue.contains(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return queue.containsAll(c);
    }

    @Override
    public Iterator<E> iterator() {
        return queue.iterator();
    }

    @Override
    public boolean remove(Object o) {
        return queue.remove(o);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return queue.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return queue.retainAll(c);
    }

    @Override
    public Object[] toArray() {
        return queue.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return queue.toArray(a);
    }
}
