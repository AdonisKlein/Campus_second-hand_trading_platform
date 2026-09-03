using System;
using System.IO;
using System.Reflection;
using System.Text;

public static class NativeArgvFixture {
    public static int Main(string[] args) {
        var config = File.ReadAllLines(Assembly.GetExecutingAssembly().Location + ".fixture");
        var exitCode = Int32.Parse(config[0]);
        var stdout = Encoding.UTF8.GetString(Convert.FromBase64String(config[1]));
        var stderr = Encoding.UTF8.GetString(Convert.FromBase64String(config[2]));
        var echoArguments = Boolean.Parse(config[3]);
        if (stdout.Length > 0) Console.Out.Write(stdout);
        if (echoArguments) {
            Console.Out.WriteLine("EXECUTABLE=" + Assembly.GetExecutingAssembly().Location);
            Console.Out.WriteLine("ARGC=" + args.Length);
            for (var index = 0; index < args.Length; index++) {
                Console.Out.WriteLine("ARGV[" + index + "]=" + Convert.ToBase64String(Encoding.UTF8.GetBytes(args[index])));
            }
        }
        if (stderr.Length > 0) Console.Error.Write(stderr);
        return exitCode;
    }
}
