"""
ChatFlow Latency Analysis Script
Analyzes latency.csv and generates statistics and throughput visualization
"""

import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
from collections import defaultdict

def load_data(filename='latency.csv'):
    """Load latency data from CSV file"""
    print(f"Loading data from {filename}...")
    df = pd.read_csv(filename)
    print(f"Loaded {len(df)} records\n")
    return df

def calculate_statistics(df):
    """Calculate and print latency statistics"""
    # Filter successful messages only (latency >= 0)
    successful = df[df['latency'] >= 0]['latency']
    
    if len(successful) == 0:
        print("No successful messages to analyze.")
        return
    
    mean = successful.mean()
    median = successful.median()
    p95 = successful.quantile(0.95)
    p99 = successful.quantile(0.99)
    min_latency = successful.min()
    max_latency = successful.max()
    
    print("=" * 50)
    print("  LATENCY STATISTICS")
    print("=" * 50)
    print(f"  Total records      : {len(successful):,}")
    print(f"  Mean response time : {mean:.2f} ms")
    print(f"  Median             : {median:.2f} ms")
    print(f"  95th percentile    : {p95:.2f} ms")
    print(f"  99th percentile    : {p99:.2f} ms")
    print(f"  Min                : {min_latency:.2f} ms")
    print(f"  Max                : {max_latency:.2f} ms")
    print("=" * 50)
    print()

def throughput_per_room(df):
    """Calculate and print throughput per room"""
    room_counts = df['roomId'].value_counts().sort_index()
    
    print("=" * 50)
    print("  THROUGHPUT PER ROOM")
    print("=" * 50)
    for room_id, count in room_counts.items():
        print(f"  Room {room_id:2d} : {count:,} messages")
    print("=" * 50)
    print()

def message_type_distribution(df):
    """Calculate and print message type distribution"""
    type_counts = df['messageType'].value_counts()
    total = len(df)
    
    print("=" * 50)
    print("  MESSAGE TYPE DISTRIBUTION")
    print("=" * 50)
    for msg_type, count in type_counts.items():
        percentage = 100.0 * count / total
        print(f"  {msg_type:6s} : {count:,} ({percentage:.1f}%)")
    print("=" * 50)
    print()

def plot_throughput_over_time(df, bucket_seconds=10):
    """
    Create throughput over time chart (messages/second in time buckets)
    """
    print(f"Generating throughput chart (bucket size: {bucket_seconds}s)...")
    
    # Convert timestamp to seconds (from milliseconds)
    df['time_seconds'] = df['timestamp'] / 1000.0
    
    # Get start time and normalize to 0
    start_time = df['time_seconds'].min()
    df['elapsed_seconds'] = df['time_seconds'] - start_time
    
    # Create time buckets
    df['bucket'] = (df['elapsed_seconds'] // bucket_seconds).astype(int)
    
    # Count messages per bucket
    throughput_data = df.groupby('bucket').size().reset_index(name='message_count')
    
    # Calculate throughput (messages per second)
    throughput_data['throughput_per_second'] = throughput_data['message_count'] / bucket_seconds
    throughput_data['time_seconds'] = throughput_data['bucket'] * bucket_seconds
    
    # Create figure
    fig, ax = plt.subplots(figsize=(12, 6))
    
    # Plot line chart
    ax.plot(throughput_data['time_seconds'], 
            throughput_data['throughput_per_second'], 
            'b-o', linewidth=2, markersize=6, label='Throughput')
    
    # Fill area under curve
    ax.fill_between(throughput_data['time_seconds'], 
                    throughput_data['throughput_per_second'], 
                    alpha=0.15, color='blue')
    
    # Labels and title
    ax.set_xlabel(f'Time (seconds)', fontsize=12)
    ax.set_ylabel('Throughput (messages/second)', fontsize=12)
    ax.set_title(f'ChatFlow Load Test - Throughput Over Time ({bucket_seconds}s buckets)', 
                 fontsize=14, fontweight='bold')
    
    # Format y-axis with comma separator
    ax.get_yaxis().set_major_formatter(plt.FuncFormatter(lambda x, p: f'{x:,.0f}'))
    
    # Add grid
    ax.grid(True, alpha=0.3, linestyle='--')
    
    # Find and annotate peak throughput
    peak_idx = throughput_data['throughput_per_second'].idxmax()
    peak_time = throughput_data.loc[peak_idx, 'time_seconds']
    peak_throughput = throughput_data.loc[peak_idx, 'throughput_per_second']
    
    ax.annotate(f'Peak: {peak_throughput:,.0f} msg/s',
                xy=(peak_time, peak_throughput),
                xytext=(peak_time, peak_throughput + peak_throughput * 0.15),
                fontsize=10, ha='center', color='darkblue', fontweight='bold',
                arrowprops=dict(arrowstyle='->', color='darkblue', lw=2))
    
    # Add average line
    avg_throughput = throughput_data['throughput_per_second'].mean()
    ax.axhline(y=avg_throughput, color='red', linestyle='--', 
               alpha=0.5, linewidth=1.5, label=f'Average: {avg_throughput:,.0f} msg/s')
    
    # Add legend
    ax.legend(fontsize=11, loc='best')
    
    plt.tight_layout()
    
    # Save charts
    plt.savefig('throughput_chart.png', dpi=150, bbox_inches='tight')
    plt.savefig('throughput_chart.pdf', bbox_inches='tight')
    
    print(f"✓ Charts saved: throughput_chart.png, throughput_chart.pdf")
    print(f"  Peak throughput: {peak_throughput:,.2f} msg/s at {peak_time:.0f}s")
    print(f"  Avg throughput:  {avg_throughput:,.2f} msg/s")
    print()

def main():
    """Main analysis function"""
    print()
    print("=" * 50)
    print("  CHATFLOW LATENCY ANALYSIS")
    print("=" * 50)
    print()
    
    # Load data
    df = load_data('latency.csv')
    
    # Calculate statistics
    calculate_statistics(df)
    
    # Throughput per room
    throughput_per_room(df)
    
    # Message type distribution
    message_type_distribution(df)
    
    # Plot throughput over time
    plot_throughput_over_time(df, bucket_seconds=10)
    
    print("=" * 50)
    print("  ANALYSIS COMPLETE")
    print("=" * 50)

if __name__ == '__main__':
    main()
